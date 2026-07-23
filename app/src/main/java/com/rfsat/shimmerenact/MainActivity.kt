package com.rfsat.shimmerenact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.rfsat.shimmerenact.data.repository.RecordingFile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rfsat.shimmerenact.data.models.ConnectionState
import com.rfsat.shimmerenact.ui.Screen
import com.rfsat.shimmerenact.ui.screens.*
import com.rfsat.shimmerenact.ui.theme.ShimmerENACTTheme
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ShimmerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Android 16 (targetSdk 36) removes the edge-to-edge opt-out; enable it
        // explicitly so insets are handled consistently on all API levels.
        // Explicit dark bar styles: the app is always dark-themed, so force light
        // (white) system-bar icons regardless of the device light/dark setting.
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            ShimmerENACTTheme {
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
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val isConnected = uiState.connectionState == ConnectionState.CONNECTED
    val recordingState by viewModel.recordingState.collectAsState()
    val hideLogTab by viewModel.hideLogTab.collectAsState()
    val immersiveMode by viewModel.immersiveMode.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current
    var showExitConfirm by remember { mutableStateOf(false) }

    // ── True full-screen (immersive) mode ─────────────────────────────────────
    // Hides the system status bar and navigation bar. BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    // lets the user swipe from an edge to reveal them temporarily, so the device
    // remains navigable without leaving the mode. Re-applied whenever the window
    // regains focus, because Android restores the bars after certain system events
    // (dialogs, permission prompts, app switching).
    LaunchedEffect(immersiveMode) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (immersiveMode) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(immersiveMode, view) {
        val window = (view.context as? Activity)?.window
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus && immersiveMode && window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }

    // If the Log tab is hidden while the user is on it, fall back to Sensors.
    LaunchedEffect(hideLogTab, currentRoute) {
        if (hideLogTab && currentRoute == Screen.Log.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // Bottom nav items (only show when appropriate)
    val showBottomNav = currentRoute in listOf(
        Screen.Home.route, Screen.Dashboard.route,
        Screen.Recordings.route, Screen.Log.route, Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = com.rfsat.shimmerenact.ui.theme.EnactDarkMid
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            if (currentRoute != Screen.Home.route) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(0) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
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
                    // ── Log tab with error-count badge (hideable in Settings) ──
                    if (!hideLogTab) {
                    val logEntries by com.rfsat.shimmerenact.data.repository.AppLog.entries.collectAsState()
                    val errorCount = remember(logEntries) {
                        logEntries.count { it.level == com.rfsat.shimmerenact.data.repository.LogLevel.ERROR }
                    }
                    NavigationBarItem(
                        selected = currentRoute == Screen.Log.route,
                        onClick = { navController.navigate(Screen.Log.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }},
                        icon = {
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
                    }   // end if (!hideLogTab)
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
                    // ── Exit: closes the app (never "selected") ────────────────
                    NavigationBarItem(
                        selected = false,
                        onClick = { showExitConfirm = true },
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                        label = { Text("Exit") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.rfsat.shimmerenact.ui.theme.EnactError,
                            selectedTextColor = com.rfsat.shimmerenact.ui.theme.EnactError,
                            indicatorColor = com.rfsat.shimmerenact.ui.theme.EnactError.copy(alpha = 0.15f),
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
                    viewModel = viewModel,
                    onNavigateToConnect = { navController.navigate(Screen.Connect.route) },
                    onNavigateToAbout = { navController.navigate(Screen.About.route) },
                    onDisconnect = {
                        viewModel.disconnect()
                    }
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
                    onBack = { navController.popBackStack() },
                    onViewFile = { rf ->
                        val encoded = Uri.encode(rf.path)
                        navController.navigate(Screen.RecordingViewer.createRoute(encoded))
                    },
                    onViewSession = { session ->
                        val encoded = Uri.encode(session.sessionId)
                        navController.navigate(Screen.SessionViewer.createRoute(encoded))
                    }
                )
            }
            composable(
                route = Screen.RecordingViewer.route,
                arguments = listOf(navArgument("filePath") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val filePath = Uri.decode(encodedPath)
                // Reconstruct RecordingFile from the sessions list
                val sessions by viewModel.sessions.collectAsState()
                val rf = sessions.flatMap { it.files }.find { it.path == filePath }
                    ?: com.rfsat.shimmerenact.data.repository.RecordingFile(
                        name = filePath.substringAfterLast("/").removeSuffix(".csv"),
                        path = filePath,
                        sizeBytes = java.io.File(filePath).length(),
                        lastModified = java.io.File(filePath).lastModified()
                    )
                RecordingViewerScreen(
                    recordingFile = rf,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.SessionViewer.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedId = backStackEntry.arguments?.getString("sessionId") ?: ""
                val sessionId = Uri.decode(encodedId)
                val sessions by viewModel.sessions.collectAsState()
                val session  = sessions.find { it.sessionId == sessionId }
                if (session != null && session.files.isNotEmpty()) {
                    RecordingViewerScreen(
                        files  = session.files,
                        title  = session.deviceName,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    // Session not yet loaded — go back
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(Screen.Log.route) {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSamplingRate = { navController.navigate(Screen.SamplingRate.route) }
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

    // ── Exit confirmation ─────────────────────────────────────────────────────
    // A bottom-bar item is easy to mis-tap, so exiting is always confirmed.
    // While a recording is in progress, exiting is blocked outright rather than
    // risking an unflushed CSV file: stopRecording() is asynchronous and would
    // not complete if the activity finished immediately.
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = {
                Text(
                    if (recordingState.isRecording) "Recording in progress" else "Exit ShimmerENACT?",
                    color = com.rfsat.shimmerenact.ui.theme.EnactOnSurface
                )
            },
            text = {
                Text(
                    if (recordingState.isRecording)
                        "Stop the current recording before exiting, so that all data is written to file."
                    else
                        "The sensor will be disconnected and the application closed.",
                    color = com.rfsat.shimmerenact.ui.theme.EnactOnSurface.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                if (!recordingState.isRecording) {
                    TextButton(onClick = {
                        showExitConfirm = false
                        viewModel.disconnect()
                        (context as? Activity)?.finish()
                    }) {
                        Text("Exit", color = com.rfsat.shimmerenact.ui.theme.EnactError)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(
                        if (recordingState.isRecording) "OK" else "Cancel",
                        color = com.rfsat.shimmerenact.ui.theme.EnactGreen
                    )
                }
            },
            containerColor = com.rfsat.shimmerenact.ui.theme.EnactSurface
        )
    }
}
