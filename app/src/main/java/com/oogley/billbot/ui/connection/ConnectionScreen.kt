package com.oogley.billbot.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oogley.billbot.data.gateway.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedUrl by viewModel.savedUrl.collectAsState()

    var url by remember(savedUrl) { mutableStateOf(savedUrl.ifEmpty { "wss://mattpc.gentoo-mackarel.ts.net" }) }

    val isConnecting = connectionState == ConnectionState.CONNECTING ||
            connectionState == ConnectionState.HANDSHAKING
    val isReconnecting = connectionState == ConnectionState.RECONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "BillBot",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Android Companion",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Gateway URL
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Gateway URL") },
            placeholder = { Text("wss://mattpc.gentoo-mackarel.ts.net") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error message
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Connect / Cancel / Retry buttons
        if (isConnecting) {
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (connectionState) {
                        ConnectionState.CONNECTING -> "Establishing connection..."
                        ConnectionState.HANDSHAKING -> "Authenticating..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isReconnecting) {
            // Show retry instead of auto-cycling
            Button(
                onClick = { viewModel.connect(url) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Retry", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        } else {
            Button(
                onClick = { viewModel.connect(url) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = url.isNotBlank()
            ) {
                Text("Connect", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tailscale handles authentication",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
