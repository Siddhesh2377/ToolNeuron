package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.navigation.TNavigation
import com.dark.tool_neuron.ui.screens.home_screen.ChatDrawerContent
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ScaffoldViewModel
import kotlinx.coroutines.launch

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val scaffoldViewModel: ScaffoldViewModel = hiltViewModel()
    val actionWindowExpanded by homeViewModel.actionWindowExpanded.collectAsStateWithLifecycle()
    val chats by homeViewModel.chats.collectAsStateWithLifecycle()
    val currentChatId by homeViewModel.currentChatId.collectAsStateWithLifecycle()

    val nextDestination = remember { scaffoldViewModel.resolveStartDestination() }

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route
            || currentRoute == NavScreens.PasswordScreen.route

    val showDrawer = currentRoute == NavScreens.HomeScreen.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showDrawer,
        drawerContent = {
            ModalDrawerSheet {
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
                    onNavigateToStore = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ModelStore.route)
                    },
                    onNavigateToGuide = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.AppGuide.route)
                    },
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                if (!isFullscreen) AppTopBar(
                    currentRoute = currentRoute,
                    actionWindowExpanded = actionWindowExpanded,
                    onActionWindowToggle = homeViewModel::toggleActionWindow,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onNavigateToStore = { navController.navigate(NavScreens.ModelStore.route) },
                    onNavigateToGuide = { navController.navigate(NavScreens.AppGuide.route) },
                )
            },
            bottomBar = {
                if (!isFullscreen) AppBottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                    onOnboardingComplete = { scaffoldViewModel.markOnboardingComplete() }
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
                    navController.navigate(NavScreens.ModelSetup.route) {
                        popUpTo(NavScreens.SetupScreen.route) { inclusive = true }
                    }
                },
                onModelSetupComplete = {
                    scaffoldViewModel.markModelSetupDone()
                    navController.navigate(NavScreens.HomeScreen.route) {
                        popUpTo(NavScreens.ModelSetup.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
