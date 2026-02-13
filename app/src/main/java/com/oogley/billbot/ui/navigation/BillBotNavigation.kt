package com.oogley.billbot.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
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
import com.oogley.billbot.ui.settings.SettingsScreen

enum class BillBotTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Chat("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Dashboard("dashboard", "Dashboard", Icons.Default.Dashboard),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun BillBotNavHost() {
    val navController = rememberNavController()
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.connectionState.collectAsState()

    // Show connection screen if not connected
    if (connectionState == ConnectionState.DISCONNECTED) {
        ConnectionScreen(viewModel = connectionViewModel)
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                BillBotTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
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
            composable(BillBotTab.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
