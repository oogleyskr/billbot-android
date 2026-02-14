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
    private var authParams: AuthParams? = null
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

    // Auth scopes from HelloOk
    private val _grantedScopes = MutableStateFlow<List<String>>(emptyList())
    val grantedScopes: StateFlow<List<String>> = _grantedScopes.asStateFlow()

    // Server info from hello-ok
    private var serverInfo: ServerInfo? = null
    private var policy: Policy? = null
    private var connId: String? = null

    // Last close code for auth error detection
    private var lastCloseCode: Int = 0

    fun connect(gatewayUrl: String, authToken: String, auth: AuthParams? = null) {
        url = gatewayUrl
        token = authToken
        authParams = auth
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
        val effectiveAuth = authParams ?: if (token.isNotEmpty()) AuthParams(token = token) else null

        val connectParams = ConnectParams(
            client = ClientInfo(
                version = "1.0.0",
                instanceId = UUID.randomUUID().toString()
            ),
            auth = effectiveAuth
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

                    // Save auth scopes
                    helloOk.auth?.let { auth ->
                        _grantedScopes.value = auth.scopes
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
                        "chat" -> {
                            parseChatStateEvent(event.payload)
                        }
                    }

                    scope.launch { _events.emit(event) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    // Handle "chat" events: { state: "delta"|"final"|"error"|"aborted", message: {...} }
    private fun parseChatStateEvent(payload: JsonElement?) {
        if (payload == null) return
        scope.launch {
            try {
                val obj = payload.jsonObject
                val state = obj["state"]?.jsonPrimitive?.contentOrNull ?: return@launch

                when (state) {
                    "delta" -> {
                        val message = obj["message"]?.jsonObject ?: return@launch
                        val text = extractTextFromMessage(message)
                        if (text != null) _chatEvents.emit(ChatEvent.Delta(text))
                    }
                    "final" -> {
                        val message = obj["message"]?.jsonObject
                        if (message != null) {
                            val text = extractTextFromMessage(message)
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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse chat event: ${e.message}")
            }
        }
    }

    // Extract text from OpenClaw message: { content: [{ type: "text", text: "..." }] }
    private fun extractTextFromMessage(message: JsonObject): String? {
        val contentArray = message["content"]
        if (contentArray != null && contentArray is kotlinx.serialization.json.JsonArray) {
            val texts = contentArray.mapNotNull { item ->
                item.jsonObject.takeIf {
                    it["type"]?.jsonPrimitive?.contentOrNull == "text"
                }?.get("text")?.jsonPrimitive?.contentOrNull
            }
            if (texts.isNotEmpty()) return texts.joinToString("")
        }
        // Fallback: content as plain string
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
        lastCloseCode = code
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

    // ---- Chat API ----

    suspend fun sendChat(message: String, sessionKey: String) {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("idempotencyKey", UUID.randomUUID().toString())
        }
        request("chat.send", params)
    }

    suspend fun sendChatWithAttachments(message: String, sessionKey: String, attachments: List<ChatAttachment>) {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("idempotencyKey", UUID.randomUUID().toString())
            putJsonArray("attachments") {
                attachments.forEach { att ->
                    addJsonObject {
                        put("type", att.type)
                        put("mimeType", att.mimeType)
                        att.fileName?.let { put("fileName", it) }
                        put("content", att.content)
                    }
                }
            }
        }
        request("chat.send", params)
    }

    suspend fun abortChat(sessionKey: String) {
        val params = buildJsonObject { put("sessionKey", sessionKey) }
        request("chat.abort", params)
    }

    suspend fun getChatHistory(sessionKey: String): JsonElement? {
        val params = buildJsonObject { put("sessionKey", sessionKey) }
        return request("chat.history", params)
    }

    // ---- Session API ----

    suspend fun resetSession(sessionKey: String) {
        val params = buildJsonObject { put("key", sessionKey) }
        request("sessions.reset", params)
    }

    suspend fun listSessions(): JsonElement? {
        return request("sessions.list")
    }

    suspend fun deleteSession(key: String, deleteTranscript: Boolean = true): JsonElement? {
        val params = buildJsonObject {
            put("key", key)
            put("deleteTranscript", deleteTranscript)
        }
        return request("sessions.delete", params)
    }

    suspend fun compactSession(key: String, maxLines: Int): JsonElement? {
        val params = buildJsonObject {
            put("key", key)
            put("maxLines", maxLines)
        }
        return request("sessions.compact", params)
    }

    suspend fun previewSessions(keys: List<String>): JsonElement? {
        val params = buildJsonObject {
            putJsonArray("keys") { keys.forEach { add(it) } }
        }
        return request("sessions.preview", params)
    }

    suspend fun patchSession(key: String, label: String? = null, agent: String? = null, model: String? = null): JsonElement? {
        val params = buildJsonObject {
            put("key", key)
            label?.let { put("label", it) }
            agent?.let { put("agentId", it) }
            model?.let { put("model", it) }
        }
        return request("sessions.patch", params)
    }

    // ---- Infrastructure API ----

    suspend fun getInfrastructure(): JsonElement? {
        return request("infrastructure")
    }

    // ---- Config API ----

    suspend fun getConfig(): JsonElement? {
        return request("config.get")
    }

    suspend fun getConfigSchema(): JsonElement? {
        return request("config.schema")
    }

    suspend fun patchConfig(patches: JsonElement): JsonElement? {
        return request("config.patch", patches)
    }

    // ---- Usage API ----

    suspend fun getSessionsUsage(): JsonElement? {
        val params = buildJsonObject {
            put("startDate", "2020-01-01")
            put("endDate", "2099-12-31")
            put("limit", 500)
        }
        return request("sessions.usage", params)
    }

    // ---- Logs API ----

    suspend fun tailLogs(cursor: String? = null, limit: Int = 200): JsonElement? {
        val params = buildJsonObject {
            cursor?.let { put("cursor", it) }
            put("limit", limit)
        }
        return request("logs.tail", params)
    }

    // ---- Health API ----

    suspend fun health(): JsonElement? {
        return request("health")
    }
}

class GatewayException(
    val code: String,
    override val message: String,
    val retryable: Boolean
) : Exception(message)
