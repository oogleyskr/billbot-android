package com.oogley.billbot.data.repository

import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.InfrastructureSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val gateway: GatewayClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getInfrastructure(): InfrastructureSnapshot {
        val result = gateway.getInfrastructure()
        return if (result != null) {
            try {
                json.decodeFromJsonElement(InfrastructureSnapshot.serializer(), result)
            } catch (e: Exception) {
                InfrastructureSnapshot(collectedAt = System.currentTimeMillis())
            }
        } else {
            InfrastructureSnapshot(collectedAt = System.currentTimeMillis())
        }
    }

    fun pollInfrastructure(intervalMs: Long = 10000): Flow<InfrastructureSnapshot> = flow {
        while (true) {
            try {
                emit(getInfrastructure())
            } catch (e: Exception) {
                emit(InfrastructureSnapshot(collectedAt = System.currentTimeMillis()))
            }
            delay(intervalMs)
        }
    }
}
