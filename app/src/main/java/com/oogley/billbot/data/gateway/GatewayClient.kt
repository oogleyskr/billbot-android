package com.oogley.billbot.data.gateway

import android.util.Log
import com.oogley.billbot.data.gateway.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    DISCONNECTED, CONNECTING, HANDSHAKING, CONNECTED, RECONNECTING
}

@Singleton
class GatewayClient @Inject constructor() {

    companion object {
        private const val TAG = "GatewayClient"
        private const val PROTOCOL_VERSION = 3
        private const val MAX_BACKOFF_MS = 30000L
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for WebSocket
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var url: String = ""
    private var token: String = ""
    private var autoReconnect = true
    private var backoffMs = INITIAL_BACKOFF_MS
    private var tickIntervalMs = 30000L
    private var lastTickTime = 0L
    private var tickWatchdogJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Pending request/response correlation
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonElement?>>()

    // Public state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<EventFrame>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<EventFrame> = _events.asSharedFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(replay = 0, extraBufferCapacity = 64)
    val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

    // Server info from hello-ok
    private var serverInfo: ServerInfo? = null
    private var policy: Policy? = null
    private var connId: String? = null

    fun connect(gatewayUrl: String, authToken: String) {
        url = gatewayUrl
        token = authToken
        autoReconnect = true
        backoffMs = INITIAL_BACKOFF_MS
        doConnect()
    }

    fun disconnect() {
        autoReconnect = false
        tickWatchdogJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        failAllPending("Disconnected")
    }

    private fun doConnect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.HANDSHAKING) return

        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _connectionState.value = ConnectionState.HANDSHAKING
                performHandshake(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                handleDisconnect(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                handleDisconnect(1006, t.message ?: "Connection failed")
            }
        })
    }

    private fun performHandshake(ws: WebSocket) {
        val connectParams = ConnectParams(
            client = ClientInfo(
                version = "1.0.0",
                instanceId = UUID.randomUUID().toString()
            ),
            auth = if (token.isNotEmpty()) AuthParams(token = token) else null
        )

        val requestId = UUID.randomUUID().toString()
        val frame = RequestFrame(
            id = requestId,
            method = "connect",
            params = json.encodeToJsonElement(ConnectParams.serializer(), connectParams)
        )

        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[requestId] = deferred

        ws.send(json.encodeToString(RequestFrame.serializer(), frame))

        scope.launch {
            try {
                val result = withTimeout(10000) { deferred.await() }
                if (result != null) {
                    val helloOk = json.decodeFromJsonElement(HelloOk.serializer(), result)
                    serverInfo = helloOk.server
                    policy = helloOk.policy
                    connId = helloOk.server.connId
                    tickIntervalMs = helloOk.policy.tickIntervalMs.toLong()

                    // Save device token if provided
                    helloOk.auth?.let { auth ->
                        // TODO: persist auth.deviceToken via UserPreferences
                    }

                    _connectionState.value = ConnectionState.CONNECTED
                    backoffMs = INITIAL_BACKOFF_MS
                    startTickWatchdog()
                    Log.i(TAG, "Connected to ${helloOk.server.version} (${helloOk.server.connId})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Handshake failed: ${e.message}")
                ws.close(1000, "Handshake failed")
                handleDisconnect()
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val raw = json.decodeFromString(RawFrame.serializer(), text)

            when (raw.type) {
                "res" -> {
                    val id = raw.id ?: return
                    val deferred = pendingRequests.remove(id)
                    if (deferred != null) {
                        if (raw.ok == true) {
                            deferred.complete(raw.payload)
                        } else {
                            val errMsg = raw.error?.message ?: "Unknown error"
                            deferred.completeExceptionally(GatewayException(
                                raw.error?.code ?: "UNKNOWN",
                                errMsg,
                                raw.error?.retryable ?: false
                            ))
                        }
                    }
                }
                "event" -> {
                    val event = EventFrame(
                        type = raw.type,
                        event = raw.event ?: return,
                        payload = raw.payload,
                        seq = raw.seq,
                        stateVersion = raw.stateVersion
                    )

                    when (event.event) {
                        "tick" -> {
                            lastTickTime = System.currentTimeMillis()
                        }
                        "chat", "agent" -> {
                            parseChatEvent(event.event, event.payload)
                        }
                    }

                    scope.launch { _events.emit(event) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun parseChatEvent(eventName: String, payload: JsonElement?) {
        if (payload == null) return
        scope.launch {
            try {
                val obj = payload.jsonObject

                when (eventName) {
                    "chat" -> parseChatStateEvent(obj)
                    "agent" -> parseAgentStreamEvent(obj)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse $eventName event: ${e.message}")
            }
        }
    }

    // "chat" events use { state: "delta"|"final"|"error"|"aborted", message: {...} }
    private suspend fun parseChatStateEvent(obj: JsonObject) {
        val state = obj["state"]?.jsonPrimitive?.contentOrNull ?: return

        when (state) {
            "delta" -> {
                val message = obj["message"]?.jsonObject ?: return
                val text = extractTextFromContent(message)
                if (text != null) _chatEvents.emit(ChatEvent.Delta(text))
            }
            "final" -> {
                val message = obj["message"]?.jsonObject
                if (message != null) {
                    val text = extractTextFromContent(message)
                    if (text != null) _chatEvents.emit(ChatEvent.Delta(text))
                }
                _chatEvents.emit(ChatEvent.Completed)
            }
            "error" -> {
                val msg = obj["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                _chatEvents.emit(ChatEvent.Error(msg))
            }
            "aborted" -> {
                _chatEvents.emit(ChatEvent.Completed)
            }
        }
    }

    // "agent" events use { stream: "assistant"|"tool"|"lifecycle", data: {...} }
    private suspend fun parseAgentStreamEvent(obj: JsonObject) {
        val stream = obj["stream"]?.jsonPrimitive?.contentOrNull ?: return
        val data = obj["data"]?.jsonObject

        when (stream) {
            "assistant" -> {
                val text = data?.get("text")?.jsonPrimitive?.contentOrNull
                if (text != null) _chatEvents.emit(ChatEvent.Delta(text))
            }
            "tool" -> {
                val phase = data?.get("phase")?.jsonPrimitive?.contentOrNull
                val name = data?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                when (phase) {
                    "call", "start" -> _chatEvents.emit(ChatEvent.ToolCall(name, ""))
                    "result", "end" -> {
                        val result = data?.get("result")?.jsonPrimitive?.contentOrNull
                            ?: data?.get("partialResult")?.jsonPrimitive?.contentOrNull ?: ""
                        _chatEvents.emit(ChatEvent.ToolResult(name, result))
                    }
                }
            }
            "lifecycle" -> {
                val phase = data?.get("phase")?.jsonPrimitive?.contentOrNull
                when (phase) {
                    "start" -> _chatEvents.emit(ChatEvent.Started)
                    "end" -> _chatEvents.emit(ChatEvent.Completed)
                    "error" -> {
                        val msg = data?.get("error")?.jsonPrimitive?.contentOrNull ?: "Agent error"
                        _chatEvents.emit(ChatEvent.Error(msg))
                    }
                }
            }
        }
    }

    // Extract text from OpenClaw message format: { content: [{ type: "text", text: "..." }] }
    private fun extractTextFromContent(message: JsonObject): String? {
        // Try content array format first
        val contentArray = message["content"]
        if (contentArray != null && contentArray is kotlinx.serialization.json.JsonArray) {
            val texts = contentArray.mapNotNull { item ->
                val itemObj = item.jsonObject
                if (itemObj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    itemObj["text"]?.jsonPrimitive?.contentOrNull
                } else null
            }
            if (texts.isNotEmpty()) return texts.joinToString("")
        }
        // Fallback: content as string
        return message["content"]?.jsonPrimitive?.contentOrNull
    }

    private fun startTickWatchdog() {
        tickWatchdogJob?.cancel()
        lastTickTime = System.currentTimeMillis()
        tickWatchdogJob = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                val elapsed = System.currentTimeMillis() - lastTickTime
                if (elapsed > tickIntervalMs * 2) {
                    Log.w(TAG, "Tick timeout (${elapsed}ms), reconnecting")
                    webSocket?.close(4000, "Tick timeout")
                    break
                }
            }
        }
    }

    // Last close reason, exposed for UI
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private fun handleDisconnect(code: Int = 1006, reason: String = "Connection lost") {
        tickWatchdogJob?.cancel()
        webSocket = null
        failAllPending(reason)

        // Store the error for UI
        if (reason.isNotBlank() && reason != "Client disconnect") {
            _lastError.value = reason
        }

        // Don't auto-reconnect on auth/protocol errors (1008 = policy violation)
        val isAuthError = code == 1008 || reason.contains("device identity") ||
                reason.contains("unauthorized") || reason.contains("invalid connect")
        val wasConnected = _connectionState.value == ConnectionState.CONNECTED

        if (autoReconnect && !isAuthError && wasConnected) {
            _connectionState.value = ConnectionState.RECONNECTING
            scope.launch {
                Log.d(TAG, "Reconnecting in ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                doConnect()
            }
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun failAllPending(reason: String) {
        val exception = GatewayException("DISCONNECTED", reason, true)
        pendingRequests.values.forEach { it.completeExceptionally(exception) }
        pendingRequests.clear()
    }

    // Public API: Send RPC request and await response
    suspend fun request(method: String, params: JsonElement? = null): JsonElement? {
        val ws = webSocket ?: throw GatewayException("NOT_CONNECTED", "Not connected", true)
        val requestId = UUID.randomUUID().toString()

        val frame = RequestFrame(
            id = requestId,
            method = method,
            params = params
        )

        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[requestId] = deferred

        val sent = ws.send(json.encodeToString(RequestFrame.serializer(), frame))
        if (!sent) {
            pendingRequests.remove(requestId)
            throw GatewayException("SEND_FAILED", "Failed to send message", true)
        }

        return try {
            withTimeout(30000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            throw GatewayException("TIMEOUT", "Request timed out", true)
        }
    }

    // Public API: Send chat message (streaming events come via chatEvents flow)
    suspend fun sendChat(message: String, sessionKey: String = "android://companion") {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("idempotencyKey", UUID.randomUUID().toString())
        }
        request("chat.send", params)
        // Started event comes via "agent" lifecycle stream or first "chat" delta
    }

    // Public API: Abort current chat
    suspend fun abortChat() {
        request("chat.abort")
    }

    // Public API: Get chat history
    suspend fun getChatHistory(sessionKey: String = "android://companion"): JsonElement? {
        val params = buildJsonObject { put("sessionKey", sessionKey) }
        return request("chat.history", params)
    }

    // Public API: Get infrastructure snapshot
    suspend fun getInfrastructure(): JsonElement? {
        return request("infrastructure")
    }

    // Public API: Get config
    suspend fun getConfig(): JsonElement? {
        return request("config.get")
    }

    // Public API: Get config schema
    suspend fun getConfigSchema(): JsonElement? {
        return request("config.schema")
    }

    // Public API: Patch config
    suspend fun patchConfig(patches: JsonElement): JsonElement? {
        return request("config.patch", patches)
    }

    // Public API: List sessions
    suspend fun listSessions(): JsonElement? {
        return request("sessions.list")
    }

    // Public API: Health check
    suspend fun health(): JsonElement? {
        return request("health")
    }
}

class GatewayException(
    val code: String,
    override val message: String,
    val retryable: Boolean
) : Exception(message)
