package com.oogley.billbot.ui.tokens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Memory
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

@Composable
private fun TotalView(uiState: TokensUiState) {
    // Hero card with giant token count
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

@Composable
private fun ByDeviceView(uiState: TokensUiState) {
    if (uiState.byModel.isEmpty()) {
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
        DeviceCard(model = model)
    }
}

@Composable
private fun DeviceCard(model: SessionModelUsage) {
    val displayName = formatDeviceName(model.provider, model.model)
    val total = model.totals.input + model.totals.output

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(modifier = Modifier.height(4.dp))

            // Total for this device
            Text(
                text = formatTokenCount(model.totals.totalTokens),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

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

private fun formatDeviceName(provider: String?, model: String?): String {
    return when {
        provider == "spark" && model?.contains("gpt-oss") == true -> "DGX Spark"
        provider == "heartbeat" -> "WSL2 CPU"
        provider != null -> provider.replaceFirstChar { it.uppercase() }
        model != null -> model
        else -> "Unknown"
    }
}

private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000_000 -> "%.2fB".format(tokens / 1_000_000_000.0)
        tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
        tokens >= 1_000 -> "%.1fK".format(tokens / 1_000.0)
        else -> "$tokens"
    }
}

private fun formatTokenCountFull(tokens: Long): String {
    return "%,d tokens".format(tokens)
}
