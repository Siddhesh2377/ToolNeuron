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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.data.SetupDataStore
import com.dark.tool_neuron.data.TermsDataStore
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.global.AppPaths
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screen.gate.VaultGateScreen
import com.dark.tool_neuron.ui.screen.guide.GuideScreen
import com.dark.tool_neuron.ui.screen.guide.TermsAndConditionsScreen
import com.dark.tool_neuron.ui.screen.home.HomeScreen
import com.dark.tool_neuron.ui.screen.memory.AiMemoryScreen
import com.dark.tool_neuron.ui.screen.memory.VaultDashboard
import com.dark.tool_neuron.ui.screen.model_config.ModelConfigEditorScreen
import com.dark.tool_neuron.ui.screen.model_store.ModelStoreScreen
import com.dark.tool_neuron.ui.screen.settings.SettingsScreen
import com.dark.tool_neuron.ui.screen.setup.ImageGenSetupScreen
import com.dark.tool_neuron.ui.screen.setup.SetupScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.viewmodel.VaultGateViewModel
import com.dark.tool_neuron.worker.LlmModelWorker
import com.dark.tool_neuron.worker.NotificationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

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
                var needsMigration by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        // Parallelize DataStore reads — each opens a separate file
                        val (termsAccepted, setupDone, guideSeen) = coroutineScope {
                            val t = async { TermsDataStore(context).hasAcceptedTerms.first() }
                            val s = async { SetupDataStore(context).isSetupDone.first() }
                            val g = async { AppSettingsDataStore(context).guideSeen.first() }
                            Triple(t.await(), s.await(), g.await())
                        }

                        // Auto-init vault for returning users (exists on disk but not yet opened)
                        if (!VaultManager.isReady.value && VaultManager.exists(context)) {
                            VaultManager.initPlaintext(context)
                            AppContainer.ensureVaultInitialized()
                        }

                        val vaultReady = VaultManager.isReady.value

                        // Check for legacy data that needs migration
                        val roomDb = context.getDatabasePath("llm_models_database").exists()
                        val vaultFile = AppPaths.vaultFile(context).exists()
                        needsMigration = roomDb || vaultFile

                        // Only check models if vault is ready
                        val hasModel = if (vaultReady) {
                            try {
                                val modelRepository = AppContainer.getModelRepository()
                                val models = modelRepository.getAllModels().first()
                                models.any {
                                    it.providerType == ProviderType.GGUF || it.providerType == ProviderType.DIFFUSION
                                }
                            } catch (_: Exception) { false }
                        } else false
                        hasModelsInstalled = hasModel

                        startDestination = when {
                            // Returning user: vault ready + terms accepted + (setup done or has model)
                            vaultReady && termsAccepted && (setupDone || hasModel) -> Screen.Chat.route

                            // Vault ready but terms not accepted
                            vaultReady && !termsAccepted -> Screen.Terms.route

                            // Vault ready but setup not done
                            vaultReady && !setupDone && !hasModel -> Screen.OnboardingSetup.route

                            // First launch: show intro
                            !guideSeen -> Screen.Intro.route

                            // Needs migration and vault not ready: go to migration
                            needsMigration && !vaultReady -> Screen.Migration.route

                            // Guide seen but terms not accepted
                            !termsAccepted -> Screen.Terms.route

                            // Fallback: go to setup (which handles vault init if needed)
                            else -> Screen.OnboardingSetup.route
                        }
                    }
                }

                val dest = startDestination ?: return@NeuroVerseTheme

                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        startDestination = dest,
                        hasModelsInstalled = hasModelsInstalled,
                        needsMigration = needsMigration
                    )

                    val apiCallActive by AppStateManager.apiCallActive.collectAsState()
                    val apiCallType by AppStateManager.apiCallType.collectAsState()
                    val apiCallModel by AppStateManager.apiCallModel.collectAsState()
                    val apiCallDetails by AppStateManager.apiCallDetails.collectAsState()

                    ApiStatusOverlay(
                        visible = apiCallActive,
                        type = apiCallType,
                        model = apiCallModel,
                        details = apiCallDetails
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
    // Onboarding (flat routes so any can be used as startDestination)
    object Intro : Screen("intro")
    object Guide : Screen("guide")
    object Migration : Screen("migration")
    object Terms : Screen("terms")
    object OnboardingSetup : Screen("setup")

    // Main app
    object Chat : Screen("chat")
    object Store : Screen("store")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object VaultManager : Screen("vault_manager")
    object AiMemory : Screen("ai_memory")
    object ImageGenSetup : Screen("image_gen_setup")
}

@Composable
fun AppNavigation(
    startDestination: String,
    hasModelsInstalled: Boolean,
    needsMigration: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    // Activity-scoped ViewModels for shared state across navigation
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
        composable(Screen.Intro.route) {
            LaunchedEffect(Unit) {
                val nextRoute = if (needsMigration) Screen.Migration.route else Screen.Guide.route
                navController.navigate(nextRoute) {
                    popUpTo(Screen.Intro.route) { inclusive = true }
                }
            }
        }

        composable(Screen.Migration.route) {
            val vaultGateViewModel: VaultGateViewModel = viewModel()
            VaultGateScreen(
                viewModel = vaultGateViewModel,
                onComplete = {
                    navController.navigate(Screen.Guide.route) {
                        popUpTo(Screen.Migration.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Guide.route) {
            val appSettings = remember { AppSettingsDataStore(context) }
            GuideScreen(onContinue = {
                scope.launch { appSettings.saveGuideSeen(true) }
                navController.navigate(Screen.Terms.route) {
                    popUpTo(Screen.Guide.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Terms.route) {
            val termsDataStore = remember { TermsDataStore(context) }
            TermsAndConditionsScreen(
                onAccept = {
                    scope.launch {
                        termsDataStore.acceptTerms()
                    }
                    if (hasModelsInstalled) {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.OnboardingSetup.route) {
                            popUpTo(Screen.Terms.route) { inclusive = true }
                        }
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
        composable(Screen.Chat.route) {
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
                onImageGenSetupNeeded = {
                    navController.navigate(Screen.ImageGenSetup.route)
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
                onAiMemoryClick = { navController.navigate(Screen.AiMemory.route) }
            )
        }

        composable(Screen.VaultManager.route) {
            VaultDashboard(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.AiMemory.route) {
            AiMemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImageGenSetup.route) {
            ImageGenSetupScreen(
                onComplete = {
                    llmModelViewModel.onQnnSetupComplete()
                    navController.popBackStack()
                },
                onSkip = {
                    llmModelViewModel.onQnnSetupDismissed()
                    navController.popBackStack()
                },
                onBack = {
                    llmModelViewModel.onQnnSetupDismissed()
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun ApiStatusOverlay(
    visible: Boolean,
    type: String,
    model: String,
    details: String? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(Standards.RadiusXl)),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.98f),
                tonalElevation = 12.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(Standards.SpacingXl)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val icon = when {
                        type.contains("Chat", ignoreCase = true) -> TnIcons.Message
                        type.contains("Image", ignoreCase = true) -> TnIcons.Photo
                        else -> TnIcons.Sparkles
                    }

                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(Modifier.height(Standards.SpacingXl))

                    Text(
                        text = "Remote API",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = type,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    details?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(Standards.SpacingMd))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(Standards.RadiusMd)
                    ) {
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingXs),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(Standards.SpacingXl))

                    Text(
                        text = "Processing Request...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
