package com.oogley.billbot.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.ChatEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

@AndroidEntryPoint
class BillBotService : Service() {

    companion object {
        private const val TAG = "BillBotService"
        private const val FOREGROUND_ID = 1
        private const val NOTIFICATION_THROTTLE_MS = 5000L
    }

    @Inject lateinit var gateway: GatewayClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotificationTime = 0L
    private var chatNotificationId = 100
    private var alertNotificationId = 200

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        startForeground(FOREGROUND_ID, NotificationHelper.buildServiceNotification(this))

        // Watch chat events for background notifications
        scope.launch {
            gateway.chatEvents.collectLatest { event ->
                if (event is ChatEvent.Completed && isAppBackgrounded()) {
                    // We don't have the final text here directly — would need buffering
                    // For now, notify that a response was received
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationTime > NOTIFICATION_THROTTLE_MS) {
                        lastNotificationTime = now
                        val notification = NotificationHelper.buildChatNotification(
                            this@BillBotService,
                            "BillBot",
                            "New response received",
                            chatNotificationId++
                        )
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(chatNotificationId, notification)
                    }
                }
            }
        }

        // Watch gateway events for health/shutdown alerts
        scope.launch {
            gateway.events.collect { event ->
                when (event.event) {
                    "health" -> {
                        try {
                            val payload = event.payload?.jsonObject
                            val status = payload?.get("status")?.jsonPrimitive?.contentOrNull
                            if (status == "unhealthy") {
                                val nm = getSystemService(NotificationManager::class.java)
                                nm.notify(alertNotificationId++,
                                    NotificationHelper.buildAlertNotification(
                                        this@BillBotService,
                                        "Service Alert",
                                        "A service has become unhealthy",
                                        alertNotificationId
                                    )
                                )
                            }
                        } catch (_: Exception) { }
                    }
                    "shutdown" -> {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(alertNotificationId++,
                            NotificationHelper.buildAlertNotification(
                                this@BillBotService,
                                "Gateway Shutting Down",
                                "The BillBot gateway is shutting down",
                                alertNotificationId
                            )
                        )
                    }
                }
            }
        }

        Log.i(TAG, "BillBot foreground service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.i(TAG, "BillBot foreground service stopped")
    }

    private fun isAppBackgrounded(): Boolean {
        return try {
            !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        } catch (_: Exception) {
            true
        }
    }
}
