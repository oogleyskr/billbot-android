package com.oogley.billbot.ui.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll when new lines arrive and autoScroll is on
    LaunchedEffect(uiState.lines.size, uiState.autoScroll) {
        if (uiState.autoScroll && uiState.lines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.lines.size - 1)
        }
    }

    // Detect if user scrolled away from bottom
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= uiState.lines.size - 3
        }
    }

    LaunchedEffect(isAtBottom) {
        if (!isAtBottom && uiState.autoScroll) {
            viewModel.setAutoScroll(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isAtBottom) {
                FloatingActionButton(
                    onClick = {
                        viewModel.setAutoScroll(true)
                        scope.launch {
                            if (uiState.lines.isNotEmpty()) {
                                listState.animateScrollToItem(uiState.lines.size - 1)
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardDoubleArrowDown, contentDescription = "Scroll to bottom")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "ERROR", "WARN", "INFO", "DEBUG").forEach { level ->
                    FilterChip(
                        selected = uiState.filterLevel == level,
                        onClick = { viewModel.setFilterLevel(level) },
                        label = { Text(level, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (uiState.isLoading && uiState.lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(uiState.lines) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colorForLogLine(line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 1.dp),
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }
}

private fun colorForLogLine(line: String): Color {
    return when {
        line.contains("ERROR", ignoreCase = true) || line.contains("FATAL", ignoreCase = true) ->
            Color(0xFFEF5350) // red
        line.contains("WARN", ignoreCase = true) ->
            Color(0xFFFFCA28) // yellow
        line.contains("DEBUG", ignoreCase = true) ->
            Color(0xFF90A4AE) // dim
        else -> Color(0xFFE0E0E0) // default light
    }
}
