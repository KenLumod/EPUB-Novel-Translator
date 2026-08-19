package com.example.epubnoveltranslator.navigation

sealed class Screen(val route: String) {
    // Bottom Bar tabs
    object Recent : Screen("recent")
    object Models : Screen("models")
    object Settings : Screen("settings")

    // Detail screens
    object NovelDetail : Screen("novel/{novelId}") {
        fun createRoute(novelId: String) = "novel/$novelId"
    }

    object Conversation : Screen("conversation/{novelId}/{chapterId}") {
        fun createRoute(novelId: String, chapterId: String) = "conversation/$novelId/$chapterId"
    }
}
