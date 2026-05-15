package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.util.LocalIsExpandedLayout
import com.dark.tool_neuron.ui.util.ProvideWindowMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.components.RootWarningDialog
import com.dark.tool_neuron.ui.navigation.TNavigation
import com.dark.tool_neuron.ui.screens.home_screen.ChatDrawerContent
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ScaffoldViewModel
import kotlinx.coroutines.launch

@Composable
fun AppScaffold() {
    ProvideWindowMetrics {
        AppScaffoldInner()
    }
}

@Composable
private fun AppScaffoldInner() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val scaffoldViewModel: ScaffoldViewModel = hiltViewModel()
    val actionWindowExpanded by homeViewModel.actionWindowExpanded.collectAsStateWithLifecycle()
    val pillState by homeViewModel.pillState.collectAsStateWithLifecycle()
    val chats by homeViewModel.chats.collectAsStateWithLifecycle()
    val currentChatId by homeViewModel.currentChatId.collectAsStateWithLifecycle()

    val nextDestination = remember { scaffoldViewModel.resolveStartDestination() }
    val shouldLock by scaffoldViewModel.shouldLock.collectAsStateWithLifecycle()
    val rootWarning by scaffoldViewModel.rootWarning.collectAsStateWithLifecycle()
    val serverRunning by scaffoldViewModel.serverRunning.collectAsStateWithLifecycle()
    val downloadProgress by scaffoldViewModel.downloadProgress.collectAsStateWithLifecycle()

    LaunchedEffect(serverRunning, currentRoute) {
        if (serverRunning && currentRoute != null && currentRoute != NavScreens.ServerScreen.route) {
            navController.navigate(NavScreens.ServerScreen.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    rootWarning?.let { warning ->
        RootWarningDialog(
            rootEvidence = warning.rootEvidence,
            tamperEvidence = warning.tamperEvidence,
            a11yPackages = warning.a11yPackages,
            onAcknowledge = scaffoldViewModel::acknowledgeRootWarning,
        )
    }

    LaunchedEffect(shouldLock, currentRoute) {
        if (shouldLock && currentRoute != null &&
            currentRoute != NavScreens.PasswordScreen.route &&
            currentRoute != NavScreens.SetupScreen.route &&
            currentRoute != NavScreens.IntroScreen.route
        ) {
            navController.navigate(NavScreens.PasswordScreen.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route
            || currentRoute == NavScreens.PasswordScreen.route
            || currentRoute == NavScreens.Credits.route

    val showDrawer = currentRoute == NavScreens.HomeScreen.route && !serverRunning
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isExpanded = LocalIsExpandedLayout.current

    val drawerBody: @Composable () -> Unit = {
        ChatDrawerContent(
                    chats = chats,
                    currentChatId = currentChatId,
                    onChatSelected = { id ->
                        homeViewModel.selectChat(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        homeViewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteChat = homeViewModel::deleteChat,
                    onPinChat = homeViewModel::pinChat,
                    onExportChat = homeViewModel::exportChat,
                    onNavigateToStore = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ModelStore.route)
                    },
                    onNavigateToGuide = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.AppGuide.route)
                    },
                    onNavigateToDevNotes = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.DevNotes.route)
                    },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.Settings.route)
                    },
                    onNavigateToServer = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ServerScreen.route)
                    },
                    onNavigateToCredits = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.Credits.route)
                    },
                    onNavigateToImageTask = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ImageTask.route)
                    },
            onNavigateToPlugins = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.PluginInstall.route)
            },
        )
    }

    val mainScaffold: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isFullscreen) AppTopBar(
                    currentRoute = currentRoute,
                    pillState = pillState,
                    actionWindowExpanded = actionWindowExpanded,
                    downloadProgress = downloadProgress,
                    onActionWindowToggle = homeViewModel::toggleActionWindow,
                    onMenuClick = {
                        if (!isExpanded) scope.launch { drawerState.open() }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToStore = { navController.navigate(NavScreens.ModelStore.route) },
                )
            },
            bottomBar = {
                if (!isFullscreen) AppBottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                    onOnboardingComplete = { scaffoldViewModel.markOnboardingComplete() },
                    onThemeSetupComplete = {
                        navController.navigate(NavScreens.ModelSetup.route) {
                            popUpTo(NavScreens.SetupTheme.route) { inclusive = true }
                        }
                    },
                    onRagSetupComplete = {
                        scaffoldViewModel.markModelSetupDone()
                        navController.navigate(NavScreens.HomeScreen.route) {
                            popUpTo(NavScreens.SetupRag.route) { inclusive = true }
                        }
                    },
                    onTermsAccepted = {
                        val cameFromOnboarding = navController.previousBackStackEntry == null
                        scaffoldViewModel.markTermsAccepted()
                        if (cameFromOnboarding) {
                            navController.navigate(NavScreens.DevNotes.route) {
                                popUpTo(NavScreens.TermsConditions.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            },
        ) { innerPadding ->
            TNavigation(
                navController = navController,
                innerPadding = innerPadding,
                startDestination = NavScreens.IntroScreen.route,
                nextDestination = nextDestination,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowDismiss = homeViewModel::collapseActionWindow,
                onUnlocked = {
                    navController.navigate(NavScreens.HomeScreen.route) {
                        popUpTo(NavScreens.PasswordScreen.route) { inclusive = true }
                    }
                },
                onSetupComplete = {
                    navController.navigate(NavScreens.SetupTheme.route) {
                        popUpTo(NavScreens.SetupScreen.route) { inclusive = true }
                    }
                },
                onModelSetupComplete = {
                    navController.navigate(NavScreens.SetupRag.route) {
                        popUpTo(NavScreens.ModelSetup.route) { inclusive = true }
                    }
                },
                onRagSetupComplete = {
                    scaffoldViewModel.markModelSetupDone()
                    navController.navigate(NavScreens.HomeScreen.route) {
                        popUpTo(NavScreens.SetupRag.route) { inclusive = true }
                    }
                }
            )
        }
    }

    if (isExpanded && showDrawer && !isFullscreen) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(modifier = Modifier.width(320.dp)) { drawerBody() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { mainScaffold() }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showDrawer,
            drawerContent = { ModalDrawerSheet { drawerBody() } },
        ) { mainScaffold() }
    }
}
