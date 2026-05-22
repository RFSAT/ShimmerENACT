package com.rfsat.shimmerenact.ui

sealed class Screen(val route: String) {
    object Home       : Screen("home")
    object Connect    : Screen("connect")
    object Dashboard  : Screen("dashboard")
    object Settings      : Screen("settings")
    object SamplingRate  : Screen("sampling_rate")
    object Recordings    : Screen("recordings")
    object Log        : Screen("log")
    object About      : Screen("about")
    object RecordingViewer : Screen("recording_viewer/{filePath}") {
        fun createRoute(encodedPath: String) = "recording_viewer/$encodedPath"
    }
    // Multi-signal viewer — passes the sessionId; viewer loads all files from that session
    object SessionViewer : Screen("session_viewer/{sessionId}") {
        fun createRoute(sessionId: String) = "session_viewer/$sessionId"
    }
}
