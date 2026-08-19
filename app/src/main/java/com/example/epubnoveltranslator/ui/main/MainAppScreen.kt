package com.example.epubnoveltranslator.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.epubnoveltranslator.navigation.BottomNavItem
import com.example.epubnoveltranslator.navigation.Screen
import com.example.epubnoveltranslator.ui.screens.conversation.ConversationScreen
import com.example.epubnoveltranslator.ui.screens.detail.NovelDetailScreen
import com.example.epubnoveltranslator.ui.screens.models.ModelsScreen
import com.example.epubnoveltranslator.ui.screens.recent.RecentScreen
import com.example.epubnoveltranslator.ui.screens.settings.SettingsScreen
import com.example.epubnoveltranslator.ui.screens.conversation.TranslationProgress
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun MainAppScreen(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val activeTranslations by TranslationProgress.activeChapterIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show bottom bar only on top-level tabs
    val isBottomBarVisible = currentRoute in BottomNavItem.items.map { it.route }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // Every destination owns its own system-bar handling. Applying safe-drawing
            // insets here as well makes nested Scaffolds reserve the status/navigation
            // bar twice, leaving large blank bands above and below the reader.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isBottomBarVisible) {
                    NavigationBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        BottomNavItem.items.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Recent.route,
                modifier = Modifier.padding(innerPadding)
            ) {
            // Recent Novels Tab
            composable(Screen.Recent.route) {
                RecentScreen(
                    onNovelClick = { novelId ->
                        navController.navigate(Screen.NovelDetail.createRoute(novelId))
                    }
                )
            }

            // Models Tab
            composable(Screen.Models.route) {
                ModelsScreen()
            }

            // Settings Tab
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // Novel Detail Screen (Chapters, Glossary, Prompt)
            composable(
                route = Screen.NovelDetail.route,
                arguments = listOf(navArgument("novelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                NovelDetailScreen(
                    novelId = novelId,
                    onBackClick = { navController.popBackStack() },
                    onChapterClick = { chapterId ->
                        navController.navigate(Screen.Conversation.createRoute(novelId, chapterId))
                    }
                )
            }

            // Conversation / Chapter Translation Screen
            composable(
                route = Screen.Conversation.route,
                arguments = listOf(
                    navArgument("novelId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                ConversationScreen(
                    novelId = novelId,
                    chapterId = chapterId,
                    onBackClick = { navController.popBackStack() }
                )
            }
            }
        }

        FloatingModelChat(
            translationActive = activeTranslations.isNotEmpty(),
            onTranslationBusySendAttempt = {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "The model is still translating a chapter. Please wait until it finishes."
                    )
                }
            }
        )
    }
}
