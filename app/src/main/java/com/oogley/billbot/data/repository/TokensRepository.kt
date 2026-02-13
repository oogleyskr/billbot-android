package com.oogley.billbot.data.repository

import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.SessionsUsageResult
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching token usage data from the gateway.
 *
 * Calls `sessions.usage` RPC which scans all session JSONL files and aggregates
 * token counts by model, provider, channel, and day. The gateway caches results
 * for 30 seconds (COST_USAGE_CACHE_TTL_MS in usage.ts).
 *
 * We request days=36500 (~100 years) to get all-time data.
 * Returns empty result on failure so the UI always has something to show.
 */
@Singleton
class TokensRepository @Inject constructor(
    private val gateway: GatewayClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetch all-time usage aggregates. Returns empty result on error. */
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
