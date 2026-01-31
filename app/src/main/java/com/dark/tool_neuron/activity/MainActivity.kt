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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.data.SetupDataStore
import com.dark.tool_neuron.data.TermsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.ui.screen.EmbeddingSetupScreen
import com.dark.tool_neuron.ui.screen.ModelConfigEditorScreen
import com.dark.tool_neuron.ui.screen.ModelStoreScreen
import com.dark.tool_neuron.ui.screen.SetupScreen
import com.dark.tool_neuron.ui.screen.SettingsScreen
import com.dark.tool_neuron.ui.screen.TermsAndConditionsScreen
import com.dark.tool_neuron.ui.screen.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screen.memory.VaultDashboard
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.LlmModelWorker
import com.dark.tool_neuron.worker.NotificationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var termsDataStore: TermsDataStore

    @Inject
    lateinit var ragRepository: com.dark.tool_neuron.repo.RagRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        termsDataStore = TermsDataStore(this)

        // Bind LLM service after activity is created (Android 14+ requirement)
        LlmModelWorker.bindService(applicationContext)

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
                val hasAcceptedTerms by termsDataStore.hasAcceptedTerms.collectAsState(initial = true)
                val scope = rememberCoroutineScope()

                // Start background download if model not present
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        if (!EmbeddingEngine.isModelDownloaded(context)) {
                            LlmModelWorker.startEmbeddingModelDownload(context)
                        }
                    }
                }

                if (!hasAcceptedTerms) {
                    TermsAndConditionsScreen(
                        onAccept = {
                            scope.launch {
                                termsDataStore.acceptTerms()
                            }
                        }
                    )
                } else {
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    val llmModelViewModel: LLMModelViewModel = hiltViewModel()

                    AppNavigation(
                        chatViewModel = chatViewModel,
                        llmModelViewModel = llmModelViewModel
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear password cache when app terminates
        ragRepository.clearPasswordCache()
        LlmModelWorker.unbindService()
        AppContainer.shutdown()
    }
}

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Chat : Screen("chat")
    object Store : Screen("store")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object VaultManager: Screen("vault_manager")
}

@Composable
fun AppNavigation(
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Determine start destination: check installed models + setup skip flag
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val modelRepository = AppContainer.getModelRepository()
            val setupDataStore = SetupDataStore(context)

            val models = modelRepository.getAllModels().first()
            val skipped = setupDataStore.isSetupSkipped.first()

            val hasRequiredModel = models.any {
                it.providerType == ProviderType.GGUF || it.providerType == ProviderType.DIFFUSION
            }

            startDestination = if (hasRequiredModel || skipped) {
                Screen.Chat.route
            } else {
                Screen.Setup.route
            }
        }
    }

    val dest = startDestination ?: return

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = dest,
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

        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Editor.route) {
            ModelConfigEditorScreen(onBackClick = {
                navController.popBackStack()
            })
        }

        composable(Screen.Chat.route) { _ ->
            HomeScreen(
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onModelEditor = { navController.navigate(Screen.Editor.route) }
            )
        }

        composable(Screen.VaultManager.route) {
            VaultDashboard()
        }
    }
}