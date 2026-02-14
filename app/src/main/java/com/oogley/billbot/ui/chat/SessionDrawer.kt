package com.oogley.billbot.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oogley.billbot.data.gateway.model.SessionInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionDrawerContent(
    sessions: List<SessionInfo>,
    currentSessionKey: String,
    onSessionSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onCompactSession: (String, Int) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onClose: () -> Unit
) {
    var contextMenuSession by remember { mutableStateOf<SessionInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCompactDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var compactLines by remember { mutableStateOf("50") }

    Column(modifier = Modifier.fillMaxHeight().width(300.dp)) {
        // Header
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider()

        // Session list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(sessions, key = { it.key }) { session ->
                val isSelected = session.key == currentSessionKey

                ListItem(
                    headlineContent = {
                        Text(
                            text = session.label ?: session.key.substringAfterLast("://"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        val count = session.messageCount ?: 0
                        val time = session.lastActiveAt?.let { formatRelativeTime(it) } ?: ""
                        Text(
                            text = buildString {
                                if (count > 0) append("$count msgs")
                                if (time.isNotEmpty()) {
                                    if (count > 0) append(" \u00b7 ")
                                    append(time)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (isSelected) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            onSessionSelected(session.key)
                            onClose()
                        },
                        onLongClick = {
                            contextMenuSession = session
                        }
                    )
                )

                // Context menu dropdown
                if (contextMenuSession?.key == session.key) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { contextMenuSession = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                renameText = session.label ?: ""
                                showRenameDialog = true
                                contextMenuSession = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Compact") },
                            leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                            onClick = {
                                compactLines = "50"
                                showCompactDialog = true
                                contextMenuSession = null
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showDeleteDialog = true
                                contextMenuSession = null
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // New Session button
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    onNewSession()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Session")
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        val session = contextMenuSession ?: sessions.find { it.label == renameText || it.key == currentSessionKey }
        val sessionKey = session?.key ?: currentSessionKey
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Session Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameSession(sessionKey, renameText.trim())
                    }
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete dialog
    if (showDeleteDialog) {
        val session = contextMenuSession ?: return
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session") },
            text = { Text("Delete \"${session.label ?: session.key}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session.key)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Compact dialog
    if (showCompactDialog) {
        val session = contextMenuSession ?: return
        AlertDialog(
            onDismissRequest = { showCompactDialog = false },
            title = { Text("Compact Session") },
            text = {
                Column {
                    Text("Keep only the most recent messages.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = compactLines,
                        onValueChange = { compactLines = it.filter { c -> c.isDigit() } },
                        label = { Text("Max lines to keep") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lines = compactLines.toIntOrNull() ?: 50
                    onCompactSession(session.key, lines)
                    showCompactDialog = false
                }) { Text("Compact") }
            },
            dismissButton = {
                TextButton(onClick = { showCompactDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> "${diff / 604800_000}w ago"
    }
}
