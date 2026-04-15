package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.global.Standards

// ── Dynamic Action Window ──

enum class DynamicWindowTab {
    STATUS,
    MODELS,
    SYSTEM
}

@Composable
fun DynamicActionWindow(
    chatViewModel: ChatViewModel,
    modelViewModel: LLMModelViewModel,
    loadedRagCount: Int = 0,
    enabledToolCount: Int = 0,
    isMemoryEnabled: Boolean = false,
    ttsModelLoaded: Boolean = false
) {
    val appState by AppStateManager.appState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(DynamicWindowTab.STATUS) }
    val installedModels by modelViewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentModelID by modelViewModel.currentModelID.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.content()),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(Standards.RadiusLg)
    ) {
        Column {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = @Composable {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                DynamicWindowTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(Motion.state()) togetherWith
                            fadeOut(Motion.state())
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    DynamicWindowTab.STATUS -> StatusTabContent(
                        appState = appState,
                        chatViewModel = chatViewModel,
                        loadedRagCount = loadedRagCount,
                        enabledToolCount = enabledToolCount,
                        isMemoryEnabled = isMemoryEnabled,
                        ttsModelLoaded = ttsModelLoaded
                    )
                    DynamicWindowTab.MODELS -> ModelsTabContent(
                        installedModels,
                        currentModelID,
                        modelViewModel,
                        chatViewModel
                    )
                    DynamicWindowTab.SYSTEM -> SystemTabContent(appState, modelViewModel, chatViewModel)
                }
            }
        }
    }
}
