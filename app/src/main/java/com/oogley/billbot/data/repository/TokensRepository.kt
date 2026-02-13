package com.oogley.billbot.data.repository

import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.SessionsUsageResult
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokensRepository @Inject constructor(
    private val gateway: GatewayClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getUsage(): SessionsUsageResult {
        val result = gateway.getSessionsUsage()
        return if (result != null) {
            try {
                json.decodeFromJsonElement(SessionsUsageResult.serializer(), result)
            } catch (e: Exception) {
                SessionsUsageResult()
            }
        } else {
            SessionsUsageResult()
        }
    }
}
