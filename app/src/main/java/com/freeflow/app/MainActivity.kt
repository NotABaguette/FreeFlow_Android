package com.freeflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freeflow.app.ui.screens.*
import com.freeflow.app.ui.theme.*
import com.freeflow.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeFlowTheme {
                FreeFlowMainScreen()
            }
        }
    }
}

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Chats : Screen("chats", "Chats", Icons.Filled.Chat, Icons.Outlined.Chat)
    data object Contacts : Screen("contacts", "Contacts", Icons.Filled.People, Icons.Outlined.People)
    data object Bulletins : Screen("bulletins", "Bulletins", Icons.Filled.Newspaper, Icons.Outlined.Newspaper)
    data object Connection : Screen("connection", "Connection", Icons.Filled.Cable, Icons.Outlined.Cable)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val screens = listOf(
    Screen.Chats,
    Screen.Contacts,
    Screen.Bulletins,
    Screen.Connection,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeFlowMainScreen() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = DarkOnSurface,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FreeFlowBlue,
                            selectedTextColor = FreeFlowBlue,
                            unselectedIconColor = DarkOnSurfaceVariant,
                            unselectedTextColor = DarkOnSurfaceVariant,
                            indicatorColor = FreeFlowBlue.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chats.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(Screen.Chats.route) {
                ChatsScreen(viewModel)
            }
            composable(Screen.Contacts.route) {
                ContactsScreen(viewModel)
            }
            composable(Screen.Bulletins.route) {
                BulletinsScreen(viewModel)
            }
            composable(Screen.Connection.route) {
                ConnectionScreen(viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel)
            }
        }
    }
}
