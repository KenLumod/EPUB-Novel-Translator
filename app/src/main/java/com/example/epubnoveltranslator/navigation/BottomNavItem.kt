package com.example.epubnoveltranslator.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Recent : BottomNavItem(
        route = Screen.Recent.route,
        title = "Recent",
        icon = Icons.Default.AutoStories
    )

    object Models : BottomNavItem(
        route = Screen.Models.route,
        title = "Models",
        icon = Icons.Default.SmartToy
    )

    object Settings : BottomNavItem(
        route = Screen.Settings.route,
        title = "Settings",
        icon = Icons.Default.Tune
    )

    companion object {
        val items = listOf(Recent, Models, Settings)
    }
}
