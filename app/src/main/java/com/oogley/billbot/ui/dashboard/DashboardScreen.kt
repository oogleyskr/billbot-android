package com.oogley.billbot.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oogley.billbot.data.gateway.model.*
import com.oogley.billbot.ui.theme.StatusGreen
import com.oogley.billbot.ui.theme.StatusRed
import com.oogley.billbot.ui.theme.StatusYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snapshot = uiState.snapshot

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    // Overall status indicator
                    val overallStatus = computeOverallStatus(snapshot)
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "Status",
                        tint = overallStatus,
                        modifier = Modifier.padding(end = 16.dp).size(12.dp)
                    )
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            if (snapshot == null && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data available", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // DGX Spark GPU
                    snapshot?.gpu?.let { GpuCard(title = "DGX Spark (GB10)", gpu = it) }

                    // Inference Speed
                    snapshot?.inferenceSpeed?.let { InferenceCard(speed = it) }

                    // RTX 3090
                    snapshot?.localGpu?.let { GpuCard(title = "RTX 3090", gpu = it) }

                    // Memory Cortex (Radeon VII)
                    snapshot?.memoryCortex?.let { MemoryCortexCard(cortex = it) }

                    // Provider Health
                    snapshot?.providers?.let { ProviderCard(providers = it) }

                    // SSH Tunnels
                    snapshot?.tunnels?.let { if (it.isNotEmpty()) TunnelCard(tunnels = it) }

                    // Multimodal Services
                    snapshot?.multimodal?.let { MultimodalCard(multimodal = it) }

                    // WSL2 System Metrics
                    snapshot?.systemMetrics?.let { SystemMetricsCard(title = "WSL2 System", metrics = it) }

                    // DGX System Metrics
                    snapshot?.remoteSystemMetrics?.let { SystemMetricsCard(title = "DGX System", metrics = it) }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun StatusDot(healthy: Boolean) {
    Icon(
        Icons.Default.Circle,
        contentDescription = null,
        tint = if (healthy) StatusGreen else StatusRed,
        modifier = Modifier.size(8.dp)
    )
}

@Composable
fun MetricRow(label: String, value: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DashboardCard(
    title: String,
    icon: ImageVector,
    statusColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (statusColor != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Circle, contentDescription = null,
                        tint = statusColor, modifier = Modifier.size(10.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun GpuCard(title: String, gpu: GpuMetricsSnapshot) {
    val gpuInfo = gpu.gpus.firstOrNull()
    val statusColor = when {
        gpu.error != null -> StatusRed
        gpuInfo == null -> StatusYellow
        (gpuInfo.temperatureCelsius ?: 0.0) > 85 -> StatusRed
        (gpuInfo.temperatureCelsius ?: 0.0) > 75 -> StatusYellow
        else -> StatusGreen
    }

    DashboardCard(title = title, icon = Icons.Default.Memory, statusColor = statusColor) {
        if (gpuInfo != null) {
            gpuInfo.temperatureCelsius?.let {
                MetricRow("Temperature", "${it.toInt()}°C", Icons.Default.Thermostat)
            }
            gpuInfo.utilizationPercent?.let {
                MetricRow("GPU Utilization", "${it.toInt()}%", Icons.Default.Speed)
                LinearProgressIndicator(
                    progress = { (it / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            if (gpuInfo.memoryUsedMB != null && gpuInfo.memoryTotalMB != null) {
                val usedGB = gpuInfo.memoryUsedMB!! / 1024.0
                val totalGB = gpuInfo.memoryTotalMB!! / 1024.0
                MetricRow("VRAM", "%.1f / %.1f GB".format(usedGB, totalGB), Icons.Default.Storage)
                LinearProgressIndicator(
                    progress = { (gpuInfo.memoryUsedMB!! / gpuInfo.memoryTotalMB!!).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            gpuInfo.powerDrawWatts?.let { draw ->
                val limit = gpuInfo.powerLimitWatts
                MetricRow("Power", if (limit != null) "${draw.toInt()}W / ${limit.toInt()}W" else "${draw.toInt()}W",
                    Icons.Default.Bolt)
            }
        } else if (gpu.error != null) {
            Text(gpu.error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("No GPU data", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun InferenceCard(speed: InferenceSpeedSnapshot) {
    DashboardCard(title = "Inference", icon = Icons.Default.Speed, statusColor = StatusGreen) {
        MetricRow("Speed", "%.1f tok/s".format(speed.tokensPerSecond))
        MetricRow("Average", "%.1f tok/s".format(speed.averageTokPerSec))
        MetricRow("Completions", "${speed.completionCount}")
    }
}

@Composable
fun MemoryCortexCard(cortex: MemoryCortexSnapshot) {
    val statusColor = when (cortex.status) {
        "ok" -> StatusGreen
        "degraded" -> StatusYellow
        else -> StatusRed
    }

    DashboardCard(title = "Memory Cortex (Radeon VII)", icon = Icons.Default.Psychology, statusColor = statusColor) {
        MetricRow("Status", cortex.status.uppercase())
        cortex.modelName?.let { MetricRow("Model", it) }
        MetricRow("LLM", cortex.llmStatus.uppercase())
        MetricRow("Middleware", cortex.middlewareStatus.uppercase())
        cortex.generationTokPerSec?.let { MetricRow("Gen Speed", "%.1f tok/s".format(it)) }
        cortex.memoriesCount?.let { MetricRow("Memories", "$it") }
        cortex.kvCacheUsageRatio?.let {
            MetricRow("KV Cache", "${(it * 100).toInt()}%")
            LinearProgressIndicator(
                progress = { it.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        if (cortex.vramTotalMB > 0) {
            val usedGB = (cortex.vramUsedMB ?: 0.0) / 1024.0
            val totalGB = cortex.vramTotalMB / 1024.0
            MetricRow("VRAM", "%.1f / %.1f GB".format(usedGB, totalGB))
        }
    }
}

@Composable
fun ProviderCard(providers: ProviderHealthSnapshot) {
    val allHealthy = providers.providers.values.all { it.healthy }
    val statusColor = if (allHealthy) StatusGreen else StatusRed

    DashboardCard(title = "Providers", icon = Icons.Default.Cloud, statusColor = statusColor) {
        providers.providers.forEach { (name, status) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status.healthy)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    status.latencyMs?.let {
                        Text("${it}ms", style = MaterialTheme.typography.labelSmall)
                    }
                    if (status.consecutiveFailures > 0) {
                        Text("${status.consecutiveFailures} failures",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusRed)
                    }
                }
            }
        }
    }
}

@Composable
fun TunnelCard(tunnels: List<TunnelMonitorResult>) {
    val allReachable = tunnels.all { it.tunnel.reachable }
    val statusColor = if (allReachable) StatusGreen else StatusRed

    DashboardCard(title = "SSH Tunnels", icon = Icons.Default.Lan, statusColor = statusColor) {
        tunnels.forEach { result ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(result.tunnel.reachable)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${result.tunnel.host}:${result.tunnel.port}",
                        style = MaterialTheme.typography.bodyMedium)
                }
                result.tunnel.latencyMs?.let {
                    Text("${it}ms", style = MaterialTheme.typography.labelSmall)
                }
            }
            result.service?.let { svc ->
                Text("  ${svc.name}: ${svc.status ?: if (svc.active) "active" else "inactive"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MultimodalCard(multimodal: MultimodalHealthSnapshot) {
    val statusColor = when {
        multimodal.servicesUp == multimodal.servicesTotal -> StatusGreen
        multimodal.servicesUp > 0 -> StatusYellow
        else -> StatusRed
    }

    DashboardCard(
        title = "Multimodal (${multimodal.servicesUp}/${multimodal.servicesTotal})",
        icon = Icons.Default.Hub,
        statusColor = statusColor
    ) {
        multimodal.services.forEach { svc ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(svc.status == "ok")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(svc.label, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    svc.model?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    svc.latencyMs?.let {
                        Text("${it}ms", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemMetricsCard(title: String, metrics: SystemMetricsSnapshot) {
    val statusColor = when {
        metrics.error != null -> StatusRed
        (metrics.cpuUsagePercent ?: 0.0) > 90 -> StatusRed
        (metrics.cpuUsagePercent ?: 0.0) > 70 -> StatusYellow
        else -> StatusGreen
    }

    DashboardCard(title = title, icon = Icons.Default.Computer, statusColor = statusColor) {
        metrics.cpuUsagePercent?.let {
            MetricRow("CPU", "${it.toInt()}%")
            LinearProgressIndicator(
                progress = { (it / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        metrics.cpuTemperatureCelsius?.let { MetricRow("CPU Temp", "${it.toInt()}°C") }
        if (metrics.ramUsedMB != null && metrics.ramTotalMB != null) {
            val usedGB = metrics.ramUsedMB!! / 1024.0
            val totalGB = metrics.ramTotalMB!! / 1024.0
            MetricRow("RAM", "%.1f / %.1f GB".format(usedGB, totalGB))
            LinearProgressIndicator(
                progress = { (metrics.ramUsedMB!! / metrics.ramTotalMB!!).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        metrics.networkInKBps?.let { MetricRow("Network In", "%.1f KB/s".format(it)) }
        metrics.networkOutKBps?.let { MetricRow("Network Out", "%.1f KB/s".format(it)) }
    }
}

private fun computeOverallStatus(snapshot: InfrastructureSnapshot?): Color {
    if (snapshot == null) return StatusYellow
    val issues = mutableListOf<Boolean>()
    snapshot.providers?.let { p -> issues.add(p.providers.values.all { it.healthy }) }
    snapshot.tunnels?.let { t -> issues.add(t.all { it.tunnel.reachable }) }
    snapshot.gpu?.let { g -> issues.add(g.error == null) }
    snapshot.memoryCortex?.let { m -> issues.add(m.status == "ok") }
    snapshot.multimodal?.let { m -> issues.add(m.servicesUp == m.servicesTotal) }
    return when {
        issues.isEmpty() -> StatusYellow
        issues.all { it } -> StatusGreen
        issues.any { !it } && issues.any { it } -> StatusYellow
        else -> StatusRed
    }
}
