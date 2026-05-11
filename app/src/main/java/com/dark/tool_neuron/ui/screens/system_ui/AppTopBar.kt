package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import com.dark.tool_neuron.model.DownloadProgress
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesTopBar
import com.dark.tool_neuron.ui.screens.document.DocumentViewerTopBar
import com.dark.tool_neuron.ui.screens.documents.DocumentsTopBar
import com.dark.tool_neuron.ui.screens.guide.GuideTopBar
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenTopbar
import com.dark.tool_neuron.ui.screens.image_task.ImageTaskTopBar
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreenTopBar
import com.dark.tool_neuron.ui.screens.server.ServerTopBar
import com.dark.tool_neuron.ui.screens.settings.SettingsTopBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreenTopBar
import com.dark.tool_neuron.ui.screens.storage.StorageTopBar
import com.dark.tool_neuron.ui.screens.terms_conditions.TermsConditionsTopBar
import com.dark.tool_neuron.viewmodel.home_vm.PillState

@Composable
fun AppTopBar(
    currentRoute: String?,
    pillState: PillState,
    actionWindowExpanded: Boolean,
    downloadProgress: DownloadProgress? = null,
    onActionWindowToggle: () -> Unit,
    onMenuClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToStore: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
    onNavigateToModelManager: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenTopbar(
            pillState = pillState,
            expanded = actionWindowExpanded,
            downloadProgress = downloadProgress,
            onToggle = onActionWindowToggle,
            onMenuClick = onMenuClick,
            onStoreClick = onNavigateToStore,
            onGuideClick = onNavigateToGuide,
            onModelManagerClick = onNavigateToModelManager,
        )
        NavScreens.TermsConditions.route -> TermsConditionsTopBar()
        NavScreens.DevNotes.route -> DevNotesTopBar()
        NavScreens.PasswordScreen.route -> PasswordScreenTopBar()
        NavScreens.SetupScreen.route -> SetupScreenTopBar()
        NavScreens.ModelSetup.route -> SetupScreenTopBar()
        NavScreens.ModelStore.route -> Unit
        NavScreens.AppGuide.route -> GuideTopBar(title = "App Guide", onBack = onBack)
        NavScreens.GuideChat.route -> GuideTopBar(title = "Chat", onBack = onBack)
        NavScreens.GuideModels.route -> GuideTopBar(title = "Models", onBack = onBack)
        NavScreens.GuideRag.route -> GuideTopBar(title = "Documents and RAG", onBack = onBack)
        NavScreens.GuideVlm.route -> GuideTopBar(title = "Vision (VLM)", onBack = onBack)
        NavScreens.GuideVoice.route -> GuideTopBar(title = "Voice", onBack = onBack)
        NavScreens.GuideSecurity.route -> GuideTopBar(title = "Privacy and lock", onBack = onBack)
        NavScreens.GuideThemes.route -> GuideTopBar(title = "Themes", onBack = onBack)
        NavScreens.GuideServer.route -> GuideTopBar(title = "Remote Server", onBack = onBack)
        NavScreens.GuideResearch.route -> GuideTopBar(title = "Research", onBack = onBack)
        NavScreens.SetupTheme.route -> SetupScreenTopBar()
        NavScreens.SetupRag.route -> SetupScreenTopBar()
        NavScreens.ModelManager.route -> Unit
        NavScreens.Settings.route -> SettingsTopBar(onBack = onBack)
        NavScreens.SettingsChatRag.route -> SettingsTopBar(
            onBack = onBack,
            title = "Chat & RAG",
            subtitle = "Defaults for indexing and retrieval",
        )
        NavScreens.SettingsResearch.route -> SettingsTopBar(
            onBack = onBack,
            title = "Research",
            subtitle = "Multi-iteration web research",
        )
        NavScreens.SettingsVoice.route -> SettingsTopBar(
            onBack = onBack,
            title = "Voice",
            subtitle = "Text-to-speech and speech-to-text defaults",
        )
        NavScreens.SettingsTheming.route -> SettingsTopBar(
            onBack = onBack,
            title = "Theming",
            subtitle = "Mode and color palette",
        )
        NavScreens.SettingsPrivacy.route -> SettingsTopBar(
            onBack = onBack,
            title = "Privacy",
            subtitle = "App lock and panic PIN",
        )
        NavScreens.SettingsAbout.route -> SettingsTopBar(
            onBack = onBack,
            title = "About",
            subtitle = "App info and legal",
        )
        NavScreens.Storage.route -> StorageTopBar(onBack = onBack)
        NavScreens.ImageTask.route -> ImageTaskTopBar(onBack = onBack)
        NavScreens.ServerScreen.route -> ServerTopBar()
        NavScreens.HfExplorer.route -> GuideTopBar(title = "HF Explorer", onBack = onBack)
        NavScreens.Documents.route -> DocumentsTopBar(onBack = onBack)
        else -> {
            if (currentRoute?.startsWith("hf_repo/") == true) {
                GuideTopBar(title = "Repository", onBack = onBack)
            } else if (currentRoute?.startsWith("document/") == true) {
                DocumentViewerTopBar(title = "Document", onBack = onBack)
            } else if (currentRoute?.startsWith("model_config/") == true) Unit
            else Unit
        }
    }
}
