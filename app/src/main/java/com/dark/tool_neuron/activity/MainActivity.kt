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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.data.SetupDataStore
import com.dark.tool_neuron.data.TermsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.ui.screen.AiMemoryScreen
import com.dark.tool_neuron.ui.screen.ModelConfigEditorScreen
import com.dark.tool_neuron.ui.screen.ModelStoreScreen
import com.dark.tool_neuron.ui.screen.PersonaEditorScreen
import com.dark.tool_neuron.ui.screen.PersonaScreen
import com.dark.tool_neuron.ui.screen.SetupScreen
import com.dark.tool_neuron.ui.screen.SettingsScreen
import com.dark.tool_neuron.ui.screen.TermsAndConditionsScreen
import com.dark.tool_neuron.ui.screen.WelcomeScreen
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

    @Inject
    lateinit var ragRepository: com.dark.tool_neuron.repo.RagRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            NeuroVerseTheme {
                val context = this@MainActivity

                // Compute start destination from onboarding state + installed models
                var startDestination by remember { mutableStateOf<String?>(null) }
                var hasModelsInstalled by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        val termsDataStore = TermsDataStore(context)
                        val setupDataStore = SetupDataStore(context)
                        val modelRepository = AppContainer.getModelRepository()

                        val termsAccepted = termsDataStore.hasAcceptedTerms.first()
                        val setupDone = setupDataStore.isSetupDone.first()
                        val models = modelRepository.getAllModels().first()

                        val hasModel = models.any {
                            it.providerType == ProviderType.GGUF || it.providerType == ProviderType.DIFFUSION
                        }
                        hasModelsInstalled = hasModel

                        startDestination = when {
                            // Terms not accepted: returning user (has models) goes to terms directly, new user gets full onboarding
                            !termsAccepted && hasModel -> Screen.Terms.route
                            !termsAccepted -> Screen.Welcome.route

                            // Terms accepted but setup not done and no models: go to setup
                            !setupDone && !hasModel -> Screen.OnboardingSetup.route

                            // Everything done
                            else -> Screen.Chat.route
                        }
                    }
                }

                val dest = startDestination ?: return@NeuroVerseTheme

                AppNavigation(
                    startDestination = dest,
                    hasModelsInstalled = hasModelsInstalled
                )
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
    // Onboarding (flat routes so any can be used as startDestination)
    object Welcome : Screen("welcome")
    object Terms : Screen("terms")
    object OnboardingSetup : Screen("setup")

    // Main app
    object Chat : Screen("chat")
    object Store : Screen("store")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object VaultManager : Screen("vault_manager")
    object Personas : Screen("personas")
    object PersonaEditor : Screen("persona_editor/{personaId}") {
        fun createRoute(personaId: String? = null) = "persona_editor/${personaId ?: "new"}"
    }
    object AiMemory : Screen("ai_memory")
}

@Composable
fun AppNavigation(
    startDestination: String,
    hasModelsInstalled: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    // Activity-scoped ViewModels for shared state between Chat and Personas
    val chatViewModel: ChatViewModel = hiltViewModel()
    val llmModelViewModel: LLMModelViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination,
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

        // ============ ONBOARDING SCREENS ============
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(Screen.Terms.route)
                }
            )
        }

        composable(Screen.Terms.route) {
            val termsDataStore = remember { TermsDataStore(context) }
            TermsAndConditionsScreen(
                onAccept = {
                    scope.launch {
                        termsDataStore.acceptTerms()
                    }
                    if (hasModelsInstalled) {
                        // Returning user: skip setup, go to chat
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        // New user: proceed to setup
                        navController.navigate(Screen.OnboardingSetup.route)
                    }
                }
            )
        }

        composable(Screen.OnboardingSetup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ============ MAIN APP ROUTES ============
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
                onCharacterClick = {
                    navController.navigate(Screen.Personas.route)
                },
                chatViewModel = chatViewModel,
                llmModelViewModel = llmModelViewModel
            )
        }

        composable(Screen.Editor.route) {
            ModelConfigEditorScreen(onBackClick = {
                navController.popBackStack()
            })
        }

        composable(Screen.Store.route) {
            ModelStoreScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onModelEditor = { navController.navigate(Screen.Editor.route) },
                onPersonasClick = { navController.navigate(Screen.Personas.route) },
                onAiMemoryClick = { navController.navigate(Screen.AiMemory.route) }
            )
        }

        composable(Screen.VaultManager.route) {
            VaultDashboard(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Personas.route) {
            val activePersonaId by chatViewModel.activePersona.collectAsState()
            PersonaScreen(
                activePersonaId = activePersonaId?.id,
                onPersonaSelected = { personaId -> chatViewModel.setActivePersona(personaId) },
                onEditPersona = { personaId ->
                    navController.navigate(Screen.PersonaEditor.createRoute(personaId))
                },
                onCreatePersona = {
                    navController.navigate(Screen.PersonaEditor.createRoute(null))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PersonaEditor.route,
            arguments = listOf(
                androidx.navigation.navArgument("personaId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = "new"
                }
            )
        ) { backStackEntry ->
            val personaId = backStackEntry.arguments?.getString("personaId")
                ?.takeIf { it != "new" }
            PersonaEditorScreen(
                personaId = personaId,
                onNavigateBack = { navController.popBackStack() },
                onDeleted = {
                    // If deleted persona was active, clear selection
                    if (personaId != null && chatViewModel.activePersona.value?.id == personaId) {
                        chatViewModel.setActivePersona(null)
                    }
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AiMemory.route) {
            AiMemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
