package com.rfsat.shimmerenact

import android.Manifest
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.rfsat.shimmerenact.data.models.ConnectionState
import com.rfsat.shimmerenact.data.repository.AppLog
import com.rfsat.shimmerenact.data.repository.LogLevel
import com.rfsat.shimmerenact.ui.Screen
import com.rfsat.shimmerenact.ui.screens.*
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel
import java.text.SimpleDateFormat
import java.util.*

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
        viewModel.stopLocationUpdates()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ShimmerApp(viewModel: ShimmerViewModel) {
    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute   = currentEntry?.destination?.route
    val uiState       by viewModel.uiState.collectAsState()
    val isConnected    = uiState.connectionState == ConnectionState.CONNECTED

    // ── Location permissions ──────────────────────────────────────────────────
    val locationPerms = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    LaunchedEffect(locationPerms.allPermissionsGranted) {
        if (locationPerms.allPermissionsGranted) {
            viewModel.startLocationUpdates()
        } else {
            locationPerms.launchMultiplePermissionRequest()
        }
    }

    // ── Continue-recording dialog ─────────────────────────────────────────────
    var showContinueDialog by remember { mutableStateOf(false) }
    var previousSessionName by remember { mutableStateOf("") }
    var previousFileCount   by remember { mutableStateOf(0) }
    var previousSessionTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val last = viewModel.lastSession()
        if (last != null && last.files.isNotEmpty()) {
            previousSessionName = last.sessionId
            previousFileCount   = last.files.size
            previousSessionTime = last.startTimeMs
            showContinueDialog  = true
        }
    }

    if (showContinueDialog) {
        val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showContinueDialog = false },
            containerColor   = EnactDark,
            icon = { Icon(Icons.Default.FolderOpen, null, tint = EnactGreen) },
            title = { Text("Previous Recording Found", color = EnactOnSurface) },
            text  = {
                Text(
                    "A recording from ${fmt.format(Date(previousSessionTime))} " +
                    "exists with $previousFileCount file(s).\n\n" +
                    "Continue appending to it, or start new files?",
                    color = EnactOnSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showContinueDialog = false
                    viewModel.setPendingAppend(true)
                }) { Text("Continue", color = EnactGreen) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showContinueDialog = false
                    viewModel.setPendingAppend(false)
                }) { Text("New Files", color = EnactOnSurface.copy(alpha = 0.6f)) }
            }
        )
    }

    val showBottomNav = currentRoute in listOf(
        Screen.Home.route, Screen.Dashboard.route, Screen.Map.route,
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

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(containerColor = EnactDarkMid) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Home.route,
                        onClick  = { navigate(Screen.Home.route) },
                        icon     = { Icon(Icons.Default.Home, null) },
                        label    = { Text("Sensors") },
                        colors   = navItemColors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Dashboard.route,
                        enabled  = isConnected,
                        onClick  = { if (isConnected) navigate(Screen.Dashboard.route) },
                        icon     = { Icon(Icons.Default.Analytics, null) },
                        label    = { Text("Live") },
                        colors   = navItemColors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Map.route,
                        onClick  = { navigate(Screen.Map.route) },
                        icon     = { Icon(Icons.Default.Map, null) },
                        label    = { Text("Map") },
                        colors   = navItemColors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Recordings.route,
                        onClick  = { navigate(Screen.Recordings.route) },
                        icon     = { Icon(Icons.Default.TableChart, null) },
                        label    = { Text("Files") },
                        colors   = navItemColors
                    )
                    val logEntries by AppLog.entries.collectAsState()
                    val errorCount = remember(logEntries) {
                        logEntries.count { it.level == LogLevel.ERROR }
                    }
                    NavigationBarItem(
                        selected = currentRoute == Screen.Log.route,
                        onClick  = { navigate(Screen.Log.route) },
                        icon = {
                            BadgedBox(badge = {
                                if (errorCount > 0) Badge(containerColor = EnactError) {
                                    Text(
                                        if (errorCount > 9) "9+" else errorCount.toString(),
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }) { Icon(Icons.Default.Terminal, "Log") }
                        },
                        label  = { Text("Log") },
                        colors = navItemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel            = viewModel,
                    onNavigateToConnect  = { navController.navigate(Screen.Connect.route) },
                    onNavigateToAbout    = { navController.navigate(Screen.About.route) }
                )
            }
            composable(Screen.Connect.route) {
                ConnectScreen(
                    viewModel   = viewModel,
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
                    viewModel    = viewModel,
                    onDisconnect = {
                        viewModel.disconnect()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Map.route) {
                MapScreen(viewModel = viewModel)
            }
            composable(Screen.Recordings.route) {
                RecordingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Screen.Log.route) {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack    = { navController.popBackStack() }
                )
            }
            composable(Screen.SamplingRate.route) {
                SamplingRateScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Screen.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
