package com.dark.tool_neuron.activity

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.data.TermsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.ui.screen.ModelConfigEditorScreen
import com.dark.tool_neuron.ui.screen.ModelStoreScreen
import com.dark.tool_neuron.ui.screen.TermsAndConditionsScreen
import com.dark.tool_neuron.ui.screen.copyEmbeddingModelsFromAssets
import com.dark.tool_neuron.ui.screen.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screen.memory.VaultDashboard
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.LlmModelWorker
import com.dark.tool_neuron.worker.NotificationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var termsDataStore: TermsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        termsDataStore = TermsDataStore(this)

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
            val context = this

            NeuroVerseTheme {
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        copyEmbeddingModelsFromAssets(context) { status ->
                            Log.d("Embedding", status)
                        }
                    }
                }

                // Check if terms are accepted
                val hasAcceptedTerms by termsDataStore.hasAcceptedTerms.collectAsState(initial = true)
                val scope = rememberCoroutineScope()

                if (hasAcceptedTerms) {
                    // Create ViewModels at activity level
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    val llmModelViewModel: LLMModelViewModel = hiltViewModel()

                    AppNavigation(
                        chatViewModel = chatViewModel,
                        llmModelViewModel = llmModelViewModel
                    )
                } else {
                    // Show terms and conditions
                    TermsAndConditionsScreen(
                        onAccept = {
                            scope.launch {
                                termsDataStore.acceptTerms()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LlmModelWorker.unbindService()
        AppContainer.shutdown()
    }
}

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Store : Screen("store")
    object Editor : Screen("editor")
    object VaultManager: Screen("vault_manager")
}

@Composable
fun AppNavigation(
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
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

        composable(Screen.Editor.route) {
            ModelConfigEditorScreen(onBackClick = {
                navController.popBackStack()
            })
        }

        composable(Screen.Chat.route) { _ ->
            HomeScreen(
                onModelEditor = {
                    navController.navigate(Screen.Editor.route)
                },
                onStoreButtonClicked = {
                    navController.navigate(Screen.Store.route)
                },
                onVaultManagerClick = {
                    navController.navigate(Screen.VaultManager.route)
                },
                chatViewModel = chatViewModel,
                llmModelViewModel = llmModelViewModel
            )
        }

        composable(Screen.Store.route) {
            ModelStoreScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Store.route) {
            ModelStoreScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.VaultManager.route) {
            VaultDashboard()
        }
    }
}