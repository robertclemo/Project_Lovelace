package com.roanokeresistance.lovelace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.roanokeresistance.lovelace.auth.AuthScreen
import com.roanokeresistance.lovelace.chat.ChatScreen
import com.roanokeresistance.lovelace.map.MapScreen

private object Routes {
    const val AUTH = "auth"
    const val MAP = "map"
    const val CHAT = "chat"
}

@Composable
fun LovelaceApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(onSignedIn = {
                navController.navigate(Routes.MAP) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.MAP) { HomeShell(navController, startTab = Routes.MAP) }
        composable(Routes.CHAT) { HomeShell(navController, startTab = Routes.CHAT) }
    }
}

@Composable
private fun HomeShell(navController: androidx.navigation.NavHostController, startTab: String) {
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.MAP } == true,
                    onClick = { navController.navigateSingleTopTo(Routes.MAP) },
                    icon = { Icon(Icons.Filled.Map, contentDescription = "Map") },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.CHAT } == true,
                    onClick = { navController.navigateSingleTopTo(Routes.CHAT) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (startTab) {
                Routes.CHAT -> ChatScreen()
                else -> MapScreen()
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateSingleTopTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
