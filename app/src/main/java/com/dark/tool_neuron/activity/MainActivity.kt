package com.dark.tool_neuron.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.ui.screen.home_screen.HomeDrawerScreen
import com.dark.tool_neuron.ui.screen.home_screen.HomeScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.worker.NotificationPermissionHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            NotificationPermissionHelper.requestNotificationPermission(this) {
                if (it) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            NeuroVerseTheme {
                AppNavigation()
            }
        }
    }
}

sealed class Screen(val route: String) {
    object ChatList : Screen("chat_list")
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.ChatList.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Screen.ChatList.route) {
            HomeDrawerScreen(
                onChatSelected = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                }
            )
        }

        composable(Screen.Chat.route) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable

            val chatViewModel: ChatViewModel = viewModel(
                factory = AppContainer.getChatViewModelFactory()
            )

            HomeScreen(
                chatViewModel = chatViewModel,
                chatId = chatId,
                onMenuClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}