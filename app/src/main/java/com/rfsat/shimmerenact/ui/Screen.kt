package com.rfsat.shimmerenact.ui

sealed class Screen(val route: String) {
    object Home       : Screen("home")
    object Connect    : Screen("connect")
    object Dashboard  : Screen("dashboard")
    object Settings   : Screen("settings")
    object Recordings : Screen("recordings")
    object Log        : Screen("log")
    object About      : Screen("about")
}
