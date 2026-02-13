package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.Serializable

@Serializable
data class InfrastructureSnapshot(
    val providers: ProviderHealthSnapshot? = null,
    val tunnels: List<TunnelMonitorResult>? = null,
    val gpu: GpuMetricsSnapshot? = null,
    val localGpu: GpuMetricsSnapshot? = null,
    val multimodal: MultimodalHealthSnapshot? = null,
    val memoryCortex: MemoryCortexSnapshot? = null,
    val systemMetrics: SystemMetricsSnapshot? = null,
    val remoteSystemMetrics: SystemMetricsSnapshot? = null,
    val inferenceSpeed: InferenceSpeedSnapshot? = null,
    val collectedAt: Long = 0
)

@Serializable
data class ProviderHealthSnapshot(
    val providers: Map<String, ProviderHealthStatus> = emptyMap(),
    val checkedAt: Long = 0
)

@Serializable
data class ProviderHealthStatus(
    val provider: String,
    val baseUrl: String,
    val healthy: Boolean,
    val lastCheckedAt: Long = 0,
    val lastHealthyAt: Long? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val consecutiveFailures: Int = 0
)

@Serializable
data class GpuMetricsSnapshot(
    val host: String = "",
    val gpus: List<GpuMetrics> = emptyList(),
    val collectedAt: Long = 0,
    val error: String? = null
)

@Serializable
data class GpuMetrics(
    val name: String = "",
    val index: Int = 0,
    val temperatureCelsius: Double? = null,
    val utilizationPercent: Double? = null,
    val memoryUsedMB: Double? = null,
    val memoryTotalMB: Double? = null,
    val memoryUtilizationPercent: Double? = null,
    val powerDrawWatts: Double? = null,
    val powerLimitWatts: Double? = null
)

@Serializable
data class TunnelMonitorResult(
    val tunnel: TunnelStatus,
    val service: TunnelService? = null,
    val checkedAt: Long = 0
)

@Serializable
data class TunnelStatus(
    val host: String = "",
    val port: Int = 0,
    val reachable: Boolean = false,
    val latencyMs: Long? = null,
    val checkedAt: Long = 0,
    val error: String? = null
)

@Serializable
data class TunnelService(
    val name: String = "",
    val active: Boolean = false,
    val status: String? = null
)

@Serializable
data class MultimodalHealthSnapshot(
    val services: List<MultimodalServiceStatus> = emptyList(),
    val servicesUp: Int = 0,
    val servicesTotal: Int = 0,
    val checkedAt: Long = 0
)

@Serializable
data class MultimodalServiceStatus(
    val label: String = "",
    val host: String = "",
    val port: Int = 0,
    val status: String = "error", // "ok" | "loading" | "error"
    val model: String? = null,
    val service: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null
)

@Serializable
data class MemoryCortexSnapshot(
    val status: String = "error", // "ok" | "degraded" | "error"
    val llmStatus: String = "error", // "ok" | "error"
    val llmEndpoint: String = "",
    val modelName: String? = null,
    val gpuName: String = "",
    val vramTotalMB: Double = 0.0,
    val vramUsedMB: Double? = null,
    val generationTokPerSec: Double? = null,
    val promptTokPerSec: Double? = null,
    val kvCacheUsageRatio: Double? = null,
    val kvCacheTokens: Int? = null,
    val requestsProcessing: Int? = null,
    val middlewareStatus: String = "error", // "ok" | "error"
    val middlewareEndpoint: String = "",
    val memoriesCount: Int? = null,
    val middlewareLatencyMs: Long? = null,
    val llmLatencyMs: Long? = null,
    val collectedAt: Long = 0,
    val error: String? = null
)

@Serializable
data class SystemMetricsSnapshot(
    val cpuUsagePercent: Double? = null,
    val cpuTemperatureCelsius: Double? = null,
    val ramUsedMB: Double? = null,
    val ramTotalMB: Double? = null,
    val ramUsagePercent: Double? = null,
    val networkInKBps: Double? = null,
    val networkOutKBps: Double? = null,
    val collectedAt: Long = 0,
    val error: String? = null
)

@Serializable
data class InferenceSpeedSnapshot(
    val tokensPerSecond: Double = 0.0,
    val averageTokPerSec: Double = 0.0,
    val completionCount: Int = 0,
    val lastMeasuredAt: Long = 0
)
