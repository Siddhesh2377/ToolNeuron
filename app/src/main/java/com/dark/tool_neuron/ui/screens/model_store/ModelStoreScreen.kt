package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.StoreTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreScreen(
    innerPadding: PaddingValues,
    viewModel: ModelStoreViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val filteredModels by viewModel.filteredModels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val deleteInProgress by viewModel.deleteInProgress.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar = {
            if (showSearch) {
                SearchAppBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = {
                        searchQuery = it
                        viewModel.filterModels(it)
                    },
                    onCloseSearch = {
                        showSearch = false
                        searchQuery = ""
                        viewModel.filterModels("")
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Model Store") },
                    navigationIcon = {
                        ActionButton(
                            onClickListener = onNavigateBack,
                            icon = TnIcons.ArrowLeft,
                            contentDescription = "Back"
                        )
                    },
                    actions = {
                        if (selectedTab == StoreTab.MODELS) {
                            ActionButton(
                                onClickListener = { viewModel.refreshModels() },
                                icon = TnIcons.Refresh,
                                contentDescription = "Refresh"
                            )
                            ActionButton(
                                onClickListener = { showSearch = true },
                                icon = TnIcons.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == StoreTab.MODELS,
                    onClick = { viewModel.selectTab(StoreTab.MODELS) },
                    text = {
                        Text(
                            "Store",
                            fontWeight = if (selectedTab == StoreTab.MODELS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == StoreTab.INSTALLED,
                    onClick = { viewModel.selectTab(StoreTab.INSTALLED) },
                    text = {
                        Text(
                            "Installed",
                            fontWeight = if (selectedTab == StoreTab.INSTALLED) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == StoreTab.SETTINGS,
                    onClick = { viewModel.selectTab(StoreTab.SETTINGS) },
                    text = {
                        Text(
                            "Settings",
                            fontWeight = if (selectedTab == StoreTab.SETTINGS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    StoreTab.MODELS -> BrowseModelsTab(
                        models = filteredModels,
                        isLoading = isLoading,
                        error = error,
                        downloadStates = downloadStates,
                        installedModelIds = installedModels.map { it.id }.toSet(),
                        viewModel = viewModel,
                        onDownload = { viewModel.downloadModel(it) },
                        onCancelDownload = { viewModel.cancelDownload(it) },
                        onRetry = { viewModel.loadModels() }
                    )
                    StoreTab.INSTALLED -> InstalledModelsTab(
                        models = installedModels,
                        deleteInProgress = deleteInProgress,
                        onDelete = { viewModel.deleteModel(it) },
                        onLoad = { viewModel.loadModel(it) },
                        onUnload = { viewModel.unloadModel() },
                        viewModel = viewModel
                    )
                    StoreTab.SETTINGS -> SettingsTab(
                        deviceInfo = deviceInfo,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
