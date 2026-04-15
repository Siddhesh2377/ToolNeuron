package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.ui.components.LocalCodeHighlightEnabled
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import kotlinx.coroutines.launch

// ── HomeScreen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onStoreButtonClicked: () -> Unit,
    onSettingsClick: () -> Unit,
    onVaultManagerClick: () -> Unit,
    onImageGenSetupNeeded: () -> Unit,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appSettings = remember { AppSettingsDataStore(context) }
    val codeHighlightEnabled by appSettings.codeHighlightEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val toolCallingEnabled by appSettings.toolCallingEnabled
        .collectAsStateWithLifecycle(initialValue = true)

    // Navigate to QNN setup when a diffusion model needs it
    val needsQnnSetup by llmModelViewModel.needsQnnSetup.collectAsStateWithLifecycle()
    LaunchedEffect(needsQnnSetup) {
        if (needsQnnSetup) onImageGenSetupNeeded()
    }

    // Offer to reload the last loaded model on startup
    val lastModelOffer by llmModelViewModel.lastModelOffer.collectAsStateWithLifecycle()
    lastModelOffer?.let { model ->
        ReloadModelDialog(
            modelName = model.modelName,
            modelType = model.providerType,
            onConfirm = { llmModelViewModel.acceptLastModelOffer() },
            onDismiss = { llmModelViewModel.dismissLastModelOffer() }
        )
    }

    CompositionLocalProvider(LocalCodeHighlightEnabled provides codeHighlightEnabled) {
    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                HomeDrawerScreen(
                    onVaultManagerClick = onVaultManagerClick,
                    onChatSelected = {
                        chatViewModel.loadChat(it)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    chatViewModel = chatViewModel
                )
            }
        }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    onStoreButtonClicked = onStoreButtonClicked,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSettingsClick = onSettingsClick,
                    showDynamicWindow = { chatViewModel.showDynamicWindow() }
                )
            },
            bottomBar = {
                BottomBar(
                    chatViewModel = chatViewModel,
                    llmModelViewModel = llmModelViewModel,
                    toolCallingEnabled = toolCallingEnabled
                )
            }) { paddingValues ->
            BodyContent(paddingValues, chatViewModel, llmModelViewModel = llmModelViewModel)
        }
    }
    } // CompositionLocalProvider
}
