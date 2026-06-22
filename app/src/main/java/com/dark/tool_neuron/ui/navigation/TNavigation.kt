package com.dark.tool_neuron.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.NavScreens
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.screens.credits.CreditsScreen
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesScreen
import com.dark.tool_neuron.ui.screens.downloads.DownloadsScreen
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screens.image_task.ImageTaskScreen
import com.dark.tool_neuron.ui.screens.intro_screen.IntroScreen
import com.dark.tool_neuron.ui.screens.model_config.ModelConfigScreen
import com.dark.tool_neuron.ui.screens.model_manager.BackupProgressDialog
import com.dark.tool_neuron.ui.screens.model_manager.ImportPreviewDialog
import com.dark.tool_neuron.ui.screens.model_manager.ModelManagerScreen
import com.dark.tool_neuron.ui.screens.rag_debug.RagDebugScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsSectionScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsThemingScreen
import com.dark.tool_neuron.ui.screens.storage.StorageScreen
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreen
import com.dark.tool_neuron.ui.screens.guide.AppGuideScreen
import com.dark.tool_neuron.ui.screens.guide.GuideChatScreen
import com.dark.tool_neuron.ui.screens.guide.GuideEntryKeys
import com.dark.tool_neuron.ui.screens.guide.GuideModelsScreen
import com.dark.tool_neuron.ui.screens.guide.GuideRagScreen
import com.dark.tool_neuron.ui.screens.guide.GuideSecurityScreen
import com.dark.tool_neuron.ui.screens.guide.GuideServerScreen
import com.dark.tool_neuron.ui.screens.guide.GuideThemesScreen
import com.dark.tool_neuron.ui.screens.guide.GuideVlmScreen
import com.dark.tool_neuron.ui.screens.guide.GuidePluginsScreen
import com.dark.tool_neuron.ui.screens.guide.GuideImagesScreen
import com.dark.tool_neuron.ui.screens.guide.GuideVoiceScreen
import com.dark.tool_neuron.ui.screens.model_store.ModelStoreScreen
import com.dark.tool_neuron.ui.screens.plugin_hub.PluginHubScreen
import com.dark.tool_neuron.ui.screens.plugin_install.PluginInstallScreen
import com.dark.tool_neuron.ui.screens.hf_explorer.HfExplorerScreen
import com.dark.tool_neuron.ui.screens.hf_explorer.HfRepoDetailScreen
import com.dark.tool_neuron.ui.screens.server.ServerScreen
import java.net.URLDecoder
import com.dark.tool_neuron.ui.screens.setup_screen.ModelSetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupPasswordScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupRagScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupThemeScreen
import com.dark.tool_neuron.ui.screens.terms_conditions.TermsConditionsScreen
import com.dark.tool_neuron.ui.theme.rememberNavTransitions
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ImageTaskViewModel
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.PasswordViewModel
import com.dark.tool_neuron.viewmodel.RagDebugViewModel
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.viewmodel.ThemingViewModel
import com.dark.tool_neuron.viewmodel.SetupViewModel
import com.dark.tool_neuron.viewmodel.StorageViewModel

@Composable
fun TNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    startDestination: String,
    nextDestination: String,
    actionWindowExpanded: Boolean,
    onActionWindowDismiss: () -> Unit,
    onUnlocked: () -> Unit = {},
    onSetupComplete: () -> Unit = {},
    onModelSetupComplete: () -> Unit = {},
    onRagSetupComplete: () -> Unit = {},
) {
    val transitions = rememberNavTransitions()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        composable(
            route = NavScreens.IntroScreen.route,
            exitTransition = { fadeOut(tween(durationMillis = 800)) },
        ) {
            IntroScreen(
                innerPadding = innerPadding,
                onFinish = {
                    navController.navigate(nextDestination) {
                        popUpTo(NavScreens.IntroScreen.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NavScreens.HomeScreen.route) {
            val activity = LocalContext.current as ComponentActivity
            val homeViewModel: HomeViewModel = hiltViewModel(activity)
            HomeScreen(
                innerPadding = innerPadding,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowDismiss = onActionWindowDismiss,
                onOpenModelManager = { navController.navigate(NavScreens.ModelManager.route) },
                viewModel = homeViewModel,
            )
        }
        composable(NavScreens.TermsConditions.route) {
            TermsConditionsScreen(
                innerPadding = innerPadding,
                onAccept = {},
            )
        }
        composable(NavScreens.DevNotes.route) {
            DevNotesScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.Credits.route) {
            CreditsScreen(
                innerPadding = innerPadding,
                onExit = { navController.popBackStack() },
            )
        }
        composable(NavScreens.SetupScreen.route) {
            val viewModel: SetupViewModel = hiltViewModel()
            val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
            val isConfirmStep by viewModel.isConfirmStep.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()

            if (selectedMode == "app_password") {
                SetupPasswordScreen(
                    innerPadding = innerPadding,
                    password = if (isConfirmStep) confirmPassword else password,
                    isConfirmStep = isConfirmStep,
                    error = error,
                    onDigit = viewModel::appendDigit,
                    onDelete = viewModel::deleteLast,
                    onClear = viewModel::clearAll,
                    onSubmit = {
                        viewModel.submitPassword(onSuccess = onSetupComplete)
                    },
                    onBack = viewModel::goBack
                )
            } else {
                SetupScreen(
                    innerPadding = innerPadding,
                    selectedMode = selectedMode,
                    onModeSelected = { mode ->
                        viewModel.selectMode(mode)
                        if (mode == "none") {
                            viewModel.completeWithNoLock()
                            onSetupComplete()
                        }
                    }
                )
            }
        }
        composable(NavScreens.SetupTheme.route) {
            SetupThemeScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.SetupRag.route) {
            SetupRagScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.ModelStore.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelStoreScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHfExplorer = { navController.navigate(NavScreens.HfExplorer.route) },
                onNavigateToDownloads = { navController.navigate(NavScreens.Downloads.route) },
            )
        }
        composable(NavScreens.Downloads.route) {
            DownloadsScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.ModelSetup.route) {
            val activity = LocalContext.current as ComponentActivity
            val storeVm: ModelStoreViewModel = hiltViewModel(activity)
            val backupStatus by storeVm.backupStatus.collectAsStateWithLifecycle()
            val backupProgress by storeVm.backupProgress.collectAsStateWithLifecycle()
            val importPreview by storeVm.backupImportPreview.collectAsStateWithLifecycle()
            val activeWorkCount by storeVm.activeWorkCount.collectAsStateWithLifecycle()
            val downloadLabels by storeVm.activeDownloadLabels.collectAsStateWithLifecycle()
            val installStates by storeVm.installStates.collectAsStateWithLifecycle()
            val setupInProgress by storeVm.starterSetupActive.collectAsStateWithLifecycle()
            var downloadsStarted by remember { mutableStateOf(false) }
            var restoreStarted by remember { mutableStateOf(false) }

            LaunchedEffect(setupInProgress, activeWorkCount) {
                if (!setupInProgress) return@LaunchedEffect
                if (activeWorkCount > 0) downloadsStarted = true
                if (downloadsStarted && activeWorkCount == 0) {
                    downloadsStarted = false
                    storeVm.finishStarterPackSetup(markDone = true)
                    onModelSetupComplete()
                }
            }

            LaunchedEffect(backupStatus) {
                val status = backupStatus.orEmpty()
                if (restoreStarted && status.startsWith("Imported")) {
                    restoreStarted = false
                    storeVm.clearBackupStatus()
                    onModelSetupComplete()
                }
            }

            ModelSetupScreen(
                innerPadding = innerPadding,
                onPackSelected = { packId ->
                    storeVm.beginStarterPackSetup(packId)
                    downloadsStarted = false
                },
                setupBusy = setupInProgress,
                onOpenStore = { navController.navigate(NavScreens.ModelStore.route) },
                onRestoreBackup = { uri ->
                    storeVm.previewImport(uri)
                },
                onLocalImport = { uri, name, size, type ->
                    storeVm.importLocalModel(uri, name, size, type)
                    onModelSetupComplete()
                },
                onSkip = {
                    storeVm.finishStarterPackSetup(markDone = false)
                    onModelSetupComplete()
                }
            )

            importPreview?.let { preview ->
                ImportPreviewDialog(
                    preview = preview,
                    onDismiss = storeVm::dismissImportPreview,
                    onImport = { ids, overwrite, restoreSettings ->
                        restoreStarted = true
                        storeVm.confirmPreviewImport(ids, overwrite, restoreSettings)
                    },
                )
            }

            backupProgress?.let { progress ->
                BackupProgressDialog(
                    progress = progress,
                    status = backupStatus,
                    onContinueInBackground = {
                        restoreStarted = false
                        onModelSetupComplete()
                    },
                    onCloseStatus = storeVm::clearBackupStatus,
                )
            }

            if (setupInProgress) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Downloading starter pack") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            val activeInstalls = installStates.values
                                .filter { it.isActive }
                                .map { "${it.phase.label}: ${it.displayName}" }
                            val names = (downloadLabels.values.map { it.displayName } + activeInstalls)
                                .distinct()
                                .take(4)
                            Text(
                                if (activeWorkCount > 0 && names.isNotEmpty()) {
                                    names.joinToString("\n")
                                } else {
                                    "Preparing setup..."
                                },
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            downloadsStarted = false
                            onModelSetupComplete()
                        }) { Text("Explore while downloading") }
                    },
                    dismissButton = {
                        TextButton(onClick = { navController.navigate(NavScreens.Downloads.route) }) {
                            Text("View downloads")
                        }
                    },
                )
            }
        }
        composable(NavScreens.AppGuide.route) {
            AppGuideScreen(
                innerPadding = innerPadding,
                onOpenEntry = { key ->
                    val route = when (key) {
                        GuideEntryKeys.CHAT -> NavScreens.GuideChat.route
                        GuideEntryKeys.MODELS -> NavScreens.GuideModels.route
                        GuideEntryKeys.VOICE -> NavScreens.GuideVoice.route
                        GuideEntryKeys.RAG -> NavScreens.GuideRag.route
                        GuideEntryKeys.VLM -> NavScreens.GuideVlm.route
                        GuideEntryKeys.SECURITY -> NavScreens.GuideSecurity.route
                        GuideEntryKeys.THEMES -> NavScreens.GuideThemes.route
                        GuideEntryKeys.SERVER -> NavScreens.GuideServer.route
                        GuideEntryKeys.PLUGINS -> NavScreens.GuidePlugins.route
                        GuideEntryKeys.IMAGES -> NavScreens.GuideImages.route
                        else -> null
                    }
                    route?.let { navController.navigate(it) }
                },
            )
        }
        composable(NavScreens.GuideChat.route) { GuideChatScreen(innerPadding) }
        composable(NavScreens.GuideModels.route) { GuideModelsScreen(innerPadding) }
        composable(NavScreens.GuideRag.route) { GuideRagScreen(innerPadding) }
        composable(NavScreens.GuideVlm.route) { GuideVlmScreen(innerPadding) }
        composable(NavScreens.GuideVoice.route) { GuideVoiceScreen(innerPadding) }
        composable(NavScreens.GuideSecurity.route) { GuideSecurityScreen(innerPadding) }
        composable(NavScreens.GuideThemes.route) { GuideThemesScreen(innerPadding) }
        composable(NavScreens.GuideServer.route) { GuideServerScreen(innerPadding) }
        composable(NavScreens.GuidePlugins.route) { GuidePluginsScreen(innerPadding) }
        composable(NavScreens.GuideImages.route) { GuideImagesScreen(innerPadding) }
        composable(NavScreens.PluginHub.route) {
            PluginHubScreen(
                onClose = { navController.popBackStack() }
            )
        }
        composable(NavScreens.Settings.route) {
            SettingsScreen(
                innerPadding = innerPadding,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsChatRag.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_CHAT_RAG,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsVoice.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_VOICE,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsTheming.route) {
            val viewModel: ThemingViewModel = hiltViewModel()
            SettingsThemingScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
            )
        }
        composable(NavScreens.SettingsVision.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_VISION,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsServerRoles.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_SERVER_ROLES,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsPerformance.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PERFORMANCE,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsModel.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_MODEL,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsPlugins.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PLUGINS,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsPrivacy.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PRIVACY,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsDiagnostics.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_DIAGNOSTICS,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsAbout.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_ABOUT,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.RagDebug.route) {
            val viewModel: RagDebugViewModel = hiltViewModel()
            RagDebugScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavScreens.ImageTask.route) {
            val viewModel: ImageTaskViewModel = hiltViewModel()
            ImageTaskScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onOpenStore = { navController.navigate(NavScreens.ModelStore.route) },
            )
        }
        composable(NavScreens.Storage.route) {
            val viewModel: StorageViewModel = hiltViewModel()
            StorageScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateToModelManager = {
                    navController.navigate(NavScreens.ModelManager.route)
                },
                onNavigateToStore = {
                    navController.navigate(NavScreens.ModelStore.route)
                },
            )
        }
        composable(NavScreens.ModelManager.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelManagerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditModel = { modelId ->
                    navController.navigate(NavScreens.ModelConfig.routeFor(modelId))
                },
            )
        }
        composable(
            route = NavScreens.ModelConfig.route,
            arguments = listOf(navArgument(NavScreens.ModelConfig.ARG_MODEL_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            val installed by viewModel.installedModels.collectAsStateWithLifecycle()
            val modelId = backStackEntry.arguments?.getString(NavScreens.ModelConfig.ARG_MODEL_ID)
            val model = installed.firstOrNull { it.id == modelId }
            if (model == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                var initialConfig by remember(model.id) {
                    mutableStateOf<ModelConfig?>(null)
                }
                var loaded by remember(model.id) { mutableStateOf(false) }
                LaunchedEffect(model.id) {
                    initialConfig = viewModel.getModelConfig(model.id)
                    loaded = true
                }
                if (loaded) {
                    ModelConfigScreen(
                        modelInfo = model,
                        initialConfig = initialConfig,
                        onSave = { config, providerType ->
                            viewModel.saveModelConfig(config, providerType)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(NavScreens.ServerScreen.route) {
            ServerScreen(innerPadding)
        }
        composable(NavScreens.PluginInstall.route) {
            PluginInstallScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.HfExplorer.route) {
            HfExplorerScreen(innerPadding = innerPadding, navController = navController)
        }
        composable(NavScreens.HfRepoDetail.route) { backStack ->
            val raw = backStack.arguments?.getString(NavScreens.HfRepoDetail.ARG_REPO_PATH).orEmpty()
            val decoded = URLDecoder.decode(raw, "UTF-8")
            HfRepoDetailScreen(innerPadding = innerPadding, repoPath = decoded)
        }
        composable(NavScreens.PasswordScreen.route) {
            val viewModel: PasswordViewModel = hiltViewModel()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
            val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
            val lockedUntilMs by viewModel.lockedUntilMs.collectAsStateWithLifecycle()
            val wiped by viewModel.wiped.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { viewModel.reset() }
            LaunchedEffect(unlocked) {
                if (unlocked) onUnlocked()
            }

            PasswordScreen(
                innerPadding = innerPadding,
                password = password,
                error = error,
                isVerifying = isVerifying,
                lockedUntilMs = lockedUntilMs,
                wiped = wiped,
                onDigit = viewModel::appendDigit,
                onDelete = viewModel::deleteLast,
                onClear = viewModel::clearAll,
                onSubmit = viewModel::submit,
            )
        }
    }
}
