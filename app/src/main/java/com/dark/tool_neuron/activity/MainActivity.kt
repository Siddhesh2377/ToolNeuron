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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.ui.screen.ModelStoreScreen
import com.dark.tool_neuron.ui.screen.home_screen.HomeScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.LlmModelWorker
import com.dark.tool_neuron.worker.NotificationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            NotificationPermissionHelper.requestNotificationPermission(this) {
                if (it) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        setContent {
            NeuroVerseTheme {
                // Create ViewModels at activity level
                val chatViewModel: ChatViewModel = hiltViewModel()
                val llmModelViewModel: LLMModelViewModel = hiltViewModel()

                AppNavigation(
                    chatViewModel = chatViewModel, llmModelViewModel = llmModelViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppContainer.shutdown()
        LlmModelWorker.unbindService()
    }
}

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Store : Screen("store")
}

@Composable
fun AppNavigation(
    chatViewModel: ChatViewModel, llmModelViewModel: LLMModelViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Chat.route, enterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300))
    }, exitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    }, popEnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300))
    }, popExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    }) {

        composable(Screen.Chat.route) { _ ->
            HomeScreen(
                onStoreButtonClicked = {
                    navController.navigate(Screen.Store.route)
                }, chatViewModel = chatViewModel, llmModelViewModel = llmModelViewModel
            )
        }

        composable(Screen.Store.route) {
            ModelStoreScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }
    }
}