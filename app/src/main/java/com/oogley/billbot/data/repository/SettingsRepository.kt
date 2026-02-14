package com.oogley.billbot.data.repository

import com.oogley.billbot.data.gateway.GatewayClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val gateway: GatewayClient
) {
    suspend fun getConfig(): JsonElement? {
        return gateway.getConfig()
    }

    suspend fun getSchema(): JsonElement? {
        return gateway.getConfigSchema()
    }

    suspend fun patchConfig(path: String, value: JsonElement): JsonElement? {
        val patches = buildJsonObject {
            put(path, value)
        }
        return gateway.patchConfig(patches)
    }

    suspend fun applyConfig(baseHash: String?, patch: JsonObject): JsonElement? {
        val params = buildJsonObject {
            baseHash?.let { put("baseHash", it) }
            put("patch", patch)
        }
        return gateway.patchConfig(params)
    }

    suspend fun listSessions(): JsonElement? {
        return gateway.listSessions()
    }

    suspend fun health(): JsonElement? {
        return gateway.health()
    }
}
