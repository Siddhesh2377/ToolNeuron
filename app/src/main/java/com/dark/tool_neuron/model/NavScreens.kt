package com.dark.tool_neuron.model

import java.net.URLEncoder

sealed class NavScreens(val route: String) {
    object IntroScreen : NavScreens("intro_screen")
    object HomeScreen : NavScreens("home_screen")
    object TermsConditions : NavScreens("terms_conditions")
    object DevNotes : NavScreens("dev_notes")
    object Credits : NavScreens("credits")
    object PasswordScreen : NavScreens("password_screen")
    object SetupScreen : NavScreens("setup_screen")
    object SetupTheme : NavScreens("setup_theme")
    object SetupRag : NavScreens("setup_rag")
    object ModelStore : NavScreens("model_store")
    object ModelSetup : NavScreens("model_setup")
    object AppGuide : NavScreens("app_guide")
    object GuideChat : NavScreens("guide_chat")
    object GuideModels : NavScreens("guide_models")
    object GuideRag : NavScreens("guide_rag")
    object GuideVlm : NavScreens("guide_vlm")
    object GuideVoice : NavScreens("guide_voice")
    object GuideSecurity : NavScreens("guide_security")
    object GuideThemes : NavScreens("guide_themes")
    object GuideServer : NavScreens("guide_server")
    object GuidePlugins : NavScreens("guide_plugins")
    object GuideImages : NavScreens("guide_images")
    object PluginHub : NavScreens("plugin_hub")
    object PluginInstall : NavScreens("plugin_install")
    object ModelManager : NavScreens("model_manager")
    object Settings : NavScreens("settings")
    object SettingsChatRag : NavScreens("settings_chat_rag")
    object SettingsVoice : NavScreens("settings_voice")
    object SettingsTheming : NavScreens("settings_theming")
    object SettingsPrivacy : NavScreens("settings_privacy")
    object SettingsAbout : NavScreens("settings_about")
    object SettingsPerformance : NavScreens("settings_performance")
    object SettingsModel : NavScreens("settings_model")
    object SettingsVision : NavScreens("settings_vision")
    object SettingsPlugins : NavScreens("settings_plugins")
    object Storage : NavScreens("storage")
    object ImageTask : NavScreens("image_task")
    object RagDebug : NavScreens("rag_debug")
    object ServerScreen : NavScreens("server_screen")
    object HfExplorer : NavScreens("hf_explorer")
    object HfRepoDetail : NavScreens("hf_repo/{repoPath}") {
        const val ARG_REPO_PATH = "repoPath"
        fun routeFor(repoPath: String) = "hf_repo/${URLEncoder.encode(repoPath, "UTF-8")}"
    }
    object ModelConfig : NavScreens("model_config/{modelId}") {
        const val ARG_MODEL_ID = "modelId"
        fun routeFor(modelId: String) = "model_config/$modelId"
    }
}