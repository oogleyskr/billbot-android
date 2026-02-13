package com.oogley.billbot.ui.tokens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oogley.billbot.data.gateway.model.SessionModelUsage
import com.oogley.billbot.data.gateway.model.UsageTotals
import com.oogley.billbot.ui.theme.StatusBlue
import com.oogley.billbot.ui.theme.StatusGreen
import com.oogley.billbot.ui.theme.StatusYellow

/**
 * Token Counter screen — shows all-time token usage across all BillBot devices.
 *
 * Two views toggled by a segmented button at the top:
 *   1. "All Time Total" — hero card with giant formatted number (e.g. "12.3M"),
 *      input/output breakdown with colored progress bars (blue=input, green=output),
 *      live inference speeds from infrastructure snapshot, and message count summary.
 *   2. "By Device" — one card per model/provider showing that device's token total,
 *      input vs output bars, live tok/s, and completion count.
 *
 * Data sources:
 *   - Token counts: `sessions.usage` gateway RPC (see UsageModels.kt)
 *   - Live speeds: `infrastructure` gateway RPC (inferenceSpeed, memoryCortex)
 * Pull-to-refresh triggers a fresh fetch of both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(viewModel: TokensViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Token Counter") })
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // View toggle
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.currentView == TokensView.TOTAL,
                        onClick = { viewModel.switchView(TokensView.TOTAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("All Time Total")
                    }
                    SegmentedButton(
                        selected = uiState.currentView == TokensView.BY_DEVICE,
                        onClick = { viewModel.switchView(TokensView.BY_DEVICE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("By Device")
                    }
                }

                if (uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                when (uiState.currentView) {
                    TokensView.TOTAL -> TotalView(uiState)
                    TokensView.BY_DEVICE -> ByDeviceView(uiState)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** "All Time Total" view — big hero number + speeds + input/output breakdown + message stats. */
@Composable
private fun TotalView(uiState: TokensUiState) {
    // Hero card — giant formatted token count centered (e.g. "4.2M" with "4,200,000 tokens" below)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Tokens Processed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatTokenCount(uiState.totals.totalTokens),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.animateContentSize()
            )
            if (uiState.totals.totalTokens > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTokenCountFull(uiState.totals.totalTokens),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Live inference speeds — one row per device
    if (uiState.speeds.isNotEmpty()) {
        SpeedCard(speeds = uiState.speeds)
    }

    // Input vs Output breakdown
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapVert, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Input vs Output", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            TokenBreakdownRow(
                label = "Input",
                tokens = uiState.totals.input,
                total = uiState.totals.totalTokens,
                color = StatusBlue
            )
            Spacer(modifier = Modifier.height(12.dp))
            TokenBreakdownRow(
                label = "Output",
                tokens = uiState.totals.output,
                total = uiState.totals.totalTokens,
                color = StatusGreen
            )

            if (uiState.totals.cacheRead > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cache Read",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTokenCount(uiState.totals.cacheRead),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // Message stats
    if (uiState.messages.total > 0) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubble, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Messages", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))

                StatRow("Total Messages", "${uiState.messages.total}")
                StatRow("User Messages", "${uiState.messages.user}")
                StatRow("Assistant Messages", "${uiState.messages.assistant}")
                if (uiState.messages.toolCalls > 0) {
                    StatRow("Tool Calls", "${uiState.messages.toolCalls}")
                }
                if (uiState.messages.errors > 0) {
                    StatRow("Errors", "${uiState.messages.errors}")
                }
            }
        }
    }
}

/**
 * Live inference speed card — shows tok/s for each device.
 * Data comes from the infrastructure snapshot, refreshed on pull-to-refresh.
 */
@Composable
private fun SpeedCard(speeds: List<DeviceSpeed>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inference Speed", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))

            speeds.forEach { speed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(speed.device,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text(speed.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%.1f tok/s".format(speed.tokPerSec),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = speedColor(speed.tokPerSec)
                        )
                        speed.avgTokPerSec?.let { avg ->
                            Text(
                                "avg %.1f".format(avg),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Completions count if available
                speed.completions?.let { count ->
                    if (count > 0) {
                        Text(
                            "$count completions tracked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Divider between devices (not after last)
                if (speed != speeds.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

/** "By Device" view — one card per model/provider with input/output breakdown + live speed. */
@Composable
private fun ByDeviceView(uiState: TokensUiState) {
    if (uiState.byModel.isEmpty() && uiState.speeds.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No per-device data available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    uiState.byModel.forEach { model ->
        // Find matching speed data for this model's provider
        val speed = uiState.speeds.find { speed ->
            matchesDevice(model.provider, model.model, speed.device)
        }
        DeviceCard(model = model, speed = speed)
    }

    // Show speed-only cards for devices that have speed data but no token usage yet
    // (e.g. Memory Cortex doesn't go through gateway so won't appear in byModel)
    val unmatchedSpeeds = uiState.speeds.filter { speed ->
        uiState.byModel.none { model -> matchesDevice(model.provider, model.model, speed.device) }
    }
    unmatchedSpeeds.forEach { speed ->
        SpeedOnlyDeviceCard(speed = speed)
    }
}

/** Card for a single device/model showing its token total, input/output bars, and live speed. */
@Composable
private fun DeviceCard(model: SessionModelUsage, speed: DeviceSpeed?) {
    val displayName = formatDeviceName(model.provider, model.model)
    val total = model.totals.input + model.totals.output

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Memory, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(displayName, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        if (model.model != null) {
                            Text(model.model!!, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Live tok/s badge on the right side of the header
                speed?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = speedColor(it.tokPerSec).copy(alpha = 0.15f)
                    ) {
                        Text(
                            "%.1f tok/s".format(it.tokPerSec),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = speedColor(it.tokPerSec),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Total for this device
            Text(
                text = formatTokenCount(model.totals.totalTokens),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Speed details row (avg + completions)
            speed?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    it.avgTokPerSec?.let { avg ->
                        Text("Avg: %.1f tok/s".format(avg),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    it.completions?.let { count ->
                        if (count > 0) {
                            Text("$count completions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Input / Output bars
            TokenBreakdownRow(
                label = "Input",
                tokens = model.totals.input,
                total = total,
                color = StatusBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            TokenBreakdownRow(
                label = "Output",
                tokens = model.totals.output,
                total = total,
                color = StatusGreen
            )

            if (model.totals.cacheRead > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cache Read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTokenCount(model.totals.cacheRead),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${model.count} completions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Card for a device that has infrastructure data but no token usage in sessions.usage.
 * Covers devices like RTX 3090 (multimodal) and Memory Cortex (Radeon VII, own middleware).
 */
@Composable
private fun SpeedOnlyDeviceCard(speed: DeviceSpeed) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Memory, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(speed.device, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(speed.model, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Only show tok/s badge for devices that actually do LLM inference
                if (speed.tokPerSec > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = speedColor(speed.tokPerSec).copy(alpha = 0.15f)
                    ) {
                        Text(
                            "%.1f tok/s".format(speed.tokPerSec),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = speedColor(speed.tokPerSec),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                speed.role ?: "Token usage tracked separately",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Reusable row: label + count on the right, colored progress bar below. */
@Composable
private fun TokenBreakdownRow(
    label: String,
    tokens: Long,
    total: Long,
    color: androidx.compose.ui.graphics.Color
) {
    val fraction = if (total > 0) tokens.toFloat() / total.toFloat() else 0f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTokenCount(tokens), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}

/**
 * Map provider/model to a friendly device name for the UI.
 * Known mappings: spark+gpt-oss → "DGX Spark", heartbeat → "WSL2 CPU".
 */
private fun formatDeviceName(provider: String?, model: String?): String {
    return when {
        provider == "spark" && model?.contains("gpt-oss") == true -> "DGX Spark"
        provider == "heartbeat" -> "WSL2 CPU"
        provider != null -> provider.replaceFirstChar { it.uppercase() }
        model != null -> model
        else -> "Unknown"
    }
}

/** Match a usage model entry to a DeviceSpeed entry by device name. */
private fun matchesDevice(provider: String?, model: String?, deviceName: String): Boolean {
    return when (deviceName) {
        "DGX Spark" -> provider == "spark"
        "RTX 3090" -> false // Multimodal services, not LLM tokens
        "Radeon VII" -> false // Memory Cortex doesn't appear in sessions.usage
        "WSL2 CPU" -> provider == "heartbeat"
        else -> false
    }
}

/** Color-code speed: green > 15 tok/s, yellow > 5, red otherwise. */
@Composable
private fun speedColor(tokPerSec: Double): androidx.compose.ui.graphics.Color {
    return when {
        tokPerSec >= 15.0 -> StatusGreen
        tokPerSec >= 5.0 -> StatusYellow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Format token count for display: 1234 → "1.2K", 1234567 → "1.2M", 1234567890 → "1.23B". */
private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000_000 -> "%.2fB".format(tokens / 1_000_000_000.0)
        tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
        tokens >= 1_000 -> "%.1fK".format(tokens / 1_000.0)
        else -> "$tokens"
    }
}

/** Full comma-separated count shown below the hero number: "4,200,000 tokens". */
private fun formatTokenCountFull(tokens: Long): String {
    return "%,d tokens".format(tokens)
}
