package com.oogley.billbot.ui.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.ui.chat.ChatScreen
import com.oogley.billbot.ui.connection.ConnectionScreen
import com.oogley.billbot.ui.connection.ConnectionViewModel
import com.oogley.billbot.ui.dashboard.DashboardScreen
import com.oogley.billbot.ui.logs.LogsScreen
import com.oogley.billbot.ui.settings.SettingsScreen
import com.oogley.billbot.ui.tokens.TokensScreen

enum class BillBotTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Chat("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Dashboard("dashboard", "Dashboard", Icons.Default.Dashboard),
    Tokens("tokens", "Tokens", Icons.Default.Star),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillBotNavHost() {
    val navController = rememberNavController()
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.connectionState.collectAsState()

    // Show connection screen unless fully connected
    if (connectionState != ConnectionState.CONNECTED) {
        ConnectionScreen(viewModel = connectionViewModel)
        return
    }

    // Detect keyboard using the stable Compose API (works with enableEdgeToEdge + adjustResize)
    val isKeyboardOpen = WindowInsets.isImeVisible
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom nav when keyboard is open on chat screen, or when on sub-routes
    val showBottomBar = !(isKeyboardOpen && currentRoute == BillBotTab.Chat.route) && currentRoute != "logs"

    Scaffold(
        // Don't let Scaffold consume any insets — each screen handles its own
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BillBotTab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BillBotTab.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BillBotTab.Chat.route) {
                ChatScreen()
            }
            composable(BillBotTab.Dashboard.route) {
                DashboardScreen()
            }
            composable(BillBotTab.Tokens.route) {
                TokensScreen()
            }
            composable(BillBotTab.Settings.route) {
                SettingsScreen(
                    onNavigateToLogs = { navController.navigate("logs") }
                )
            }
            composable("logs") {
                LogsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
