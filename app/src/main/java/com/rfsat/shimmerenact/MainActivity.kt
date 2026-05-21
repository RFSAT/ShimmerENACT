package com.rfsat.shimmerenact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rfsat.shimmerenact.data.models.ConnectionState
import com.rfsat.shimmerenact.data.repository.AppLog
import com.rfsat.shimmerenact.data.repository.LogLevel
import com.rfsat.shimmerenact.ui.Screen
import com.rfsat.shimmerenact.ui.screens.*
<<<<<<< HEAD
import com.rfsat.shimmerenact.ui.theme.*
=======
import com.rfsat.shimmerenact.ui.theme.ShimmerENACTTheme
>>>>>>> parent of 335abb6 (Added position)
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ShimmerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            com.rfsat.shimmerenact.ui.theme.ShimmerENACTTheme {
                ShimmerApp(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.btManager.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShimmerApp(viewModel: ShimmerViewModel) {
    val navController = rememberNavController()
<<<<<<< HEAD
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute   = currentEntry?.destination?.route
    val uiState       by viewModel.uiState.collectAsState()
    val isConnected    = uiState.connectionState == ConnectionState.CONNECTED
=======
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val isConnected = uiState.connectionState == ConnectionState.CONNECTED
>>>>>>> parent of 335abb6 (Added position)

    // Bottom nav items (only show when appropriate)
    val showBottomNav = currentRoute in listOf(
        Screen.Home.route, Screen.Dashboard.route,
<<<<<<< HEAD
        Screen.Recordings.route, Screen.Log.route
    )

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor   = EnactGreen,
        selectedTextColor   = EnactGreen,
        indicatorColor      = EnactGreen.copy(alpha = 0.15f),
        unselectedIconColor = EnactOnSurface.copy(alpha = 0.4f),
        unselectedTextColor = EnactOnSurface.copy(alpha = 0.4f)
    )

    fun navigate(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }

=======
        Screen.Recordings.route, Screen.Log.route, Screen.Settings.route
    )

>>>>>>> parent of 335abb6 (Added position)
    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = com.rfsat.shimmerenact.ui.theme.EnactDarkMid
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Home.route,
                        onClick = { navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }},
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Sensors") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            selectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            indicatorColor = com.rfsat.shimmerenact.ui.theme.EnactGreen.copy(alpha = 0.15f),
                            unselectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f),
                            unselectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Dashboard.route,
<<<<<<< HEAD
                        enabled  = isConnected,
                        onClick  = { if (isConnected) navigate(Screen.Dashboard.route) },
                        icon     = { Icon(Icons.Default.Analytics, null) },
                        label    = { Text("Live") },
                        colors   = navItemColors
=======
                        enabled = isConnected,
                        onClick = { if (isConnected) navController.navigate(Screen.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }},
                        icon = { Icon(Icons.Default.Analytics, null) },
                        label = { Text("Live") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            selectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            indicatorColor = com.rfsat.shimmerenact.ui.theme.EnactGreen.copy(alpha = 0.15f),
                            unselectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f),
                            unselectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f)
                        )
>>>>>>> parent of 335abb6 (Added position)
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Recordings.route,
                        onClick = { navController.navigate(Screen.Recordings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }},
                        icon = { Icon(Icons.Default.TableChart, null) },
                        label = { Text("Files") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            selectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            indicatorColor = com.rfsat.shimmerenact.ui.theme.EnactGreen.copy(alpha = 0.15f),
                            unselectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f),
                            unselectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
<<<<<<< HEAD
                    val logEntries by AppLog.entries.collectAsState()
=======
                    // ── Log tab with error-count badge ──────────────────────
                    val logEntries by com.rfsat.shimmerenact.data.repository.AppLog.entries.collectAsState()
>>>>>>> parent of 335abb6 (Added position)
                    val errorCount = remember(logEntries) {
                        logEntries.count { it.level == LogLevel.ERROR }
                    }
                    NavigationBarItem(
                        selected = currentRoute == Screen.Log.route,
                        onClick = { navController.navigate(Screen.Log.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }},
                        icon = {
<<<<<<< HEAD
                            BadgedBox(badge = {
                                if (errorCount > 0) Badge(containerColor = EnactError) {
                                    Text(
                                        if (errorCount > 9) "9+" else errorCount.toString(),
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
=======
                            BadgedBox(
                                badge = {
                                    if (errorCount > 0) {
                                        Badge(
                                            containerColor = com.rfsat.shimmerenact.ui.theme.EnactError
                                        ) {
                                            Text(
                                                if (errorCount > 9) "9+" else errorCount.toString(),
                                                color = androidx.compose.ui.graphics.Color.White
                                            )
                                        }
                                    }
>>>>>>> parent of 335abb6 (Added position)
                                }
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = "Log")
                            }
                        },
                        label = { Text("Log") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            selectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            indicatorColor = com.rfsat.shimmerenact.ui.theme.EnactGreen.copy(alpha = 0.15f),
                            unselectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f),
                            unselectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = { navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }},
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            selectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactGreen,
                            indicatorColor = com.rfsat.shimmerenact.ui.theme.EnactGreen.copy(alpha = 0.15f),
                            unselectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f),
                            unselectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
<<<<<<< HEAD
                    viewModel           = viewModel,
=======
                    viewModel = viewModel,
>>>>>>> parent of 335abb6 (Added position)
                    onNavigateToConnect = { navController.navigate(Screen.Connect.route) },
                    onNavigateToAbout = { navController.navigate(Screen.About.route) }
                )
            }
            composable(Screen.Connect.route) {
                ConnectScreen(
                    viewModel = viewModel,
                    onConnected = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onDisconnect = {
                        viewModel.disconnect()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Recordings.route) {
                RecordingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Log.route) {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
<<<<<<< HEAD
                    onBack    = { navController.popBackStack() }
=======
                    onBack = { navController.popBackStack() },
                    onSamplingRate = { navController.navigate(Screen.SamplingRate.route) }
>>>>>>> parent of 335abb6 (Added position)
                )
            }
            composable(Screen.SamplingRate.route) {
                SamplingRateScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
