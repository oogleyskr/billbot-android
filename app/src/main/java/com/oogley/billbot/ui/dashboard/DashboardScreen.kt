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
                    // View toggle
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.currentView == DashboardView.DEVICES,
                            onClick = { viewModel.switchView(DashboardView.DEVICES) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Devices")
                        }
                        SegmentedButton(
                            selected = uiState.currentView == DashboardView.METRICS,
                            onClick = { viewModel.switchView(DashboardView.METRICS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Metrics")
                        }
                    }

                    when (uiState.currentView) {
                        DashboardView.DEVICES -> DevicesView(snapshot)
                        DashboardView.METRICS -> MetricsView(snapshot)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Devices view — one card per device/service. */
@Composable
private fun DevicesView(snapshot: InfrastructureSnapshot?) {
    // DGX Spark GPU + model + DGX remote system metrics (network)
    snapshot?.gpu?.let { gpu ->
        val modelName = snapshot.inferenceSpeed?.let { "gpt-oss-120b" }
        GpuCard(
            title = "DGX Spark (GB10)",
            gpu = gpu,
            models = listOfNotNull(modelName?.let { "LLM" to it }),
            systemMetrics = snapshot.remoteSystemMetrics
        )
    }

    // RTX 3090 + multimodal service models
    snapshot?.localGpu?.let { gpu ->
        val multimodalModels = snapshot.multimodal?.services?.mapNotNull { svc ->
            svc.model?.let { model -> svc.label to model }
        } ?: emptyList()
        GpuCard(title = "RTX 3090", gpu = gpu, models = multimodalModels)
    }

    // Memory Cortex (Radeon VII)
    snapshot?.memoryCortex?.let { MemoryCortexCard(cortex = it) }

    // All Services — providers + tunnels + memory cortex + multimodal combined
    ServicesCard(snapshot)

    // WSL2 System Metrics
    snapshot?.systemMetrics?.let { SystemMetricsCard(title = "WSL2 System", metrics = it) }
}

/**
 * Metrics view — grouped by metric type across all devices.
 *
 * Note: Radeon VII hardware metrics (temp, utilization, power) are not available.
 * The Memory Cortex collects data from llama.cpp which only exposes VRAM and tok/s,
 * not GPU hardware stats. To add those, we'd need AMD GPU monitoring on the Windows host.
 */
@Composable
private fun MetricsView(snapshot: InfrastructureSnapshot?) {
    // Temperature — all GPU temps + CPU temps
    val temps = mutableListOf<Pair<String, Double>>()
    snapshot?.gpu?.gpus?.firstOrNull()?.temperatureCelsius?.let { temps.add("DGX Spark" to it) }
    snapshot?.localGpu?.gpus?.firstOrNull()?.temperatureCelsius?.let { temps.add("RTX 3090" to it) }
    // Radeon VII: no temp data (llama.cpp doesn't expose GPU hardware metrics)
    snapshot?.systemMetrics?.cpuTemperatureCelsius?.let { temps.add("WSL2 CPU" to it) }
    if (temps.isNotEmpty()) {
        DashboardCard(title = "Temperatures", icon = Icons.Default.Thermostat) {
            temps.forEach { (device, temp) ->
                val color = when {
                    temp > 85 -> StatusRed
                    temp > 75 -> StatusYellow
                    else -> StatusGreen
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(device, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${temp.toInt()}°C", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = color)
                }
            }
            // Note Radeon VII limitation
            if (snapshot?.memoryCortex != null) {
                Text("Radeon VII: no hardware metrics (llama.cpp limitation)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // VRAM Usage — all GPUs (Radeon VII included via Memory Cortex)
    val vram = mutableListOf<Triple<String, Double, Double>>() // name, used, total
    snapshot?.gpu?.gpus?.firstOrNull()?.let { g ->
        if (g.memoryUsedMB != null && g.memoryTotalMB != null) {
            vram.add(Triple("DGX Spark", g.memoryUsedMB!!, g.memoryTotalMB!!))
        }
    }
    snapshot?.localGpu?.gpus?.firstOrNull()?.let { g ->
        if (g.memoryUsedMB != null && g.memoryTotalMB != null) {
            vram.add(Triple("RTX 3090", g.memoryUsedMB!!, g.memoryTotalMB!!))
        }
    }
    snapshot?.memoryCortex?.let { c ->
        if (c.vramTotalMB > 0) {
            vram.add(Triple("Radeon VII", c.vramUsedMB ?: 0.0, c.vramTotalMB))
        }
    }
    if (vram.isNotEmpty()) {
        DashboardCard(title = "VRAM Usage", icon = Icons.Default.Storage) {
            vram.forEach { (device, used, total) ->
                val usedGB = used / 1024.0
                val totalGB = total / 1024.0
                MetricRow(device, "%.1f / %.1f GB".format(usedGB, totalGB))
                LinearProgressIndicator(
                    progress = { (used / total).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
        }
    }

    // GPU Utilization (Radeon VII: not available)
    val utilization = mutableListOf<Pair<String, Double>>()
    snapshot?.gpu?.gpus?.firstOrNull()?.utilizationPercent?.let { utilization.add("DGX Spark" to it) }
    snapshot?.localGpu?.gpus?.firstOrNull()?.utilizationPercent?.let { utilization.add("RTX 3090" to it) }
    if (utilization.isNotEmpty()) {
        DashboardCard(title = "GPU Utilization", icon = Icons.Default.Speed) {
            utilization.forEach { (device, pct) ->
                MetricRow(device, "${pct.toInt()}%")
                LinearProgressIndicator(
                    progress = { (pct / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            if (snapshot?.memoryCortex != null) {
                Text("Radeon VII: not available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // Power Draw (Radeon VII: not available)
    val power = mutableListOf<Triple<String, Double, Double?>>() // name, draw, limit
    snapshot?.gpu?.gpus?.firstOrNull()?.let { g ->
        g.powerDrawWatts?.let { power.add(Triple("DGX Spark", it, g.powerLimitWatts)) }
    }
    snapshot?.localGpu?.gpus?.firstOrNull()?.let { g ->
        g.powerDrawWatts?.let { power.add(Triple("RTX 3090", it, g.powerLimitWatts)) }
    }
    if (power.isNotEmpty()) {
        DashboardCard(title = "Power Draw", icon = Icons.Default.Bolt) {
            power.forEach { (device, draw, limit) ->
                val text = if (limit != null) "${draw.toInt()}W / ${limit.toInt()}W" else "${draw.toInt()}W"
                MetricRow(device, text)
                if (limit != null && limit > 0) {
                    LinearProgressIndicator(
                        progress = { (draw / limit).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
            if (snapshot?.memoryCortex != null) {
                Text("Radeon VII: not available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // CPU & RAM
    val cpuRam = mutableListOf<Pair<String, SystemMetricsSnapshot>>()
    snapshot?.systemMetrics?.let { cpuRam.add("WSL2" to it) }
    snapshot?.remoteSystemMetrics?.let { cpuRam.add("DGX Spark" to it) }
    if (cpuRam.isNotEmpty()) {
        DashboardCard(title = "CPU & RAM", icon = Icons.Default.Computer) {
            cpuRam.forEach { (host, metrics) ->
                Text(host, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                metrics.cpuUsagePercent?.let {
                    MetricRow("CPU", "${it.toInt()}%")
                    LinearProgressIndicator(
                        progress = { (it / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                if (metrics.ramUsedMB != null && metrics.ramTotalMB != null) {
                    val usedGB = metrics.ramUsedMB!! / 1024.0
                    val totalGB = metrics.ramTotalMB!! / 1024.0
                    MetricRow("RAM", "%.1f / %.1f GB".format(usedGB, totalGB))
                    LinearProgressIndicator(
                        progress = { (metrics.ramUsedMB!! / metrics.ramTotalMB!!).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    // Network
    val network = mutableListOf<Pair<String, SystemMetricsSnapshot>>()
    snapshot?.systemMetrics?.let { if (it.networkInKBps != null) network.add("WSL2" to it) }
    snapshot?.remoteSystemMetrics?.let { if (it.networkInKBps != null) network.add("DGX Spark" to it) }
    if (network.isNotEmpty()) {
        DashboardCard(title = "Network", icon = Icons.Default.Lan) {
            network.forEach { (host, metrics) ->
                Text(host, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                metrics.networkInKBps?.let { MetricRow("In", "%.1f KB/s".format(it)) }
                metrics.networkOutKBps?.let { MetricRow("Out", "%.1f KB/s".format(it)) }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    // Service Health — reuses the same ServicesCard from Devices view
    ServicesCard(snapshot)
}

// ── Shared card components ──

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

// ── Device cards (used in Devices view) ──

@Composable
fun GpuCard(
    title: String,
    gpu: GpuMetricsSnapshot,
    models: List<Pair<String, String>> = emptyList(),
    systemMetrics: SystemMetricsSnapshot? = null
) {
    val gpuInfo = gpu.gpus.firstOrNull()
    val statusColor = when {
        gpu.error != null -> StatusRed
        gpuInfo == null -> StatusYellow
        (gpuInfo.temperatureCelsius ?: 0.0) > 85 -> StatusRed
        (gpuInfo.temperatureCelsius ?: 0.0) > 75 -> StatusYellow
        else -> StatusGreen
    }

    DashboardCard(title = title, icon = Icons.Default.Memory, statusColor = statusColor) {
        // Model names
        if (models.isNotEmpty()) {
            models.forEach { (label, modelName) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(modelName, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

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

        // System metrics (CPU, RAM, Network) if provided — used for DGX Spark
        systemMetrics?.let { metrics ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            metrics.cpuUsagePercent?.let {
                MetricRow("CPU", "${it.toInt()}%")
            }
            if (metrics.ramUsedMB != null && metrics.ramTotalMB != null) {
                val usedGB = metrics.ramUsedMB!! / 1024.0
                val totalGB = metrics.ramTotalMB!! / 1024.0
                MetricRow("RAM", "%.1f / %.1f GB".format(usedGB, totalGB))
            }
            metrics.networkInKBps?.let { MetricRow("Network In", "%.1f KB/s".format(it)) }
            metrics.networkOutKBps?.let { MetricRow("Network Out", "%.1f KB/s".format(it)) }
        }
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

/** Unified services card — providers, tunnels, memory cortex, and multimodal in one card. */
@Composable
fun ServicesCard(snapshot: InfrastructureSnapshot?) {
    // Compute overall health across all services
    val checks = mutableListOf<Boolean>()
    snapshot?.providers?.providers?.values?.forEach { checks.add(it.healthy) }
    snapshot?.tunnels?.forEach { checks.add(it.tunnel.reachable) }
    snapshot?.memoryCortex?.let { checks.add(it.status == "ok") }
    snapshot?.multimodal?.services?.forEach { checks.add(it.status == "ok") }
    val statusColor = when {
        checks.isEmpty() -> StatusYellow
        checks.all { it } -> StatusGreen
        checks.none { it } -> StatusRed
        else -> StatusYellow
    }

    DashboardCard(title = "Services", icon = Icons.Default.Cloud, statusColor = statusColor) {
        // LLM Providers
        snapshot?.providers?.providers?.forEach { (name, status) ->
            ServiceRow(
                label = name,
                healthy = status.healthy,
                detail = status.latencyMs?.let { "${it}ms" },
                error = if (status.consecutiveFailures > 0) "${status.consecutiveFailures} failures" else null
            )
        }

        // SSH Tunnels
        snapshot?.tunnels?.forEach { result ->
            ServiceRow(
                label = "Tunnel: ${result.tunnel.host}:${result.tunnel.port}",
                healthy = result.tunnel.reachable,
                detail = result.tunnel.latencyMs?.let { "${it}ms" }
            )
        }

        // Memory Cortex
        snapshot?.memoryCortex?.let { cortex ->
            ServiceRow(
                label = "Memory Cortex",
                healthy = cortex.status == "ok",
                detail = cortex.llmLatencyMs?.let { "${it}ms" }
            )
        }

        // Multimodal Services
        snapshot?.multimodal?.services?.forEach { svc ->
            ServiceRow(
                label = svc.label,
                healthy = svc.status == "ok",
                detail = svc.latencyMs?.let { "${it}ms" }
            )
        }
    }
}

@Composable
private fun ServiceRow(label: String, healthy: Boolean, detail: String? = null, error: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(healthy)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            detail?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = StatusRed)
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
