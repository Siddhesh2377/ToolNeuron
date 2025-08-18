package com.dark.neuroverse.activity

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.Screen
import com.dark.neuroverse.ui.screens.HomeScreen
import com.dark.neuroverse.ui.screens.IntroScreen
import com.dark.neuroverse.ui.screens.ModelsScreen
import com.dark.neuroverse.ui.screens.SettingsScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.util.makeToast
import com.dark.userdata.getDefaultBrainStructure
import com.dark.userdata.ntds.getBrainFilePath
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.saveEncryptedTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permission = Manifest.permission.POST_NOTIFICATIONS
        val requestNotificationPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) "Permission denied".makeToast(this)
            }

        // For deep-linking into a specific plugin/tab
        val pluginName = intent.getStringExtra("plugin_name")
        Log.d("MainActivity", "plugin_name extra: $pluginName")

        setContent {
            val navController = rememberNavController()
            var isInitializing by remember { mutableStateOf(true) }

            // Kick off runtime permission (no-op on old Android versions)
            LaunchedEffect(Unit) { requestNotificationPermission.launch(permission) }

            // App bootstrap: ensure brain file, then choose start destination.
            LaunchedEffect(Unit) {
                // Non-blocking: prepare encrypted brain tree if missing
                withContext(Dispatchers.IO) { ensureBrainFileExists() }

                // Decide where to start: Home if a model exists or pluginName is provided, else Models
                val hasModel = ModelManager.isAnyModelInstalled()
                val startScreen = when {
                    pluginName != null -> Screen.Home.route
                    hasModel -> Screen.Home.route
                    else -> Screen.Model.route
                }
                navController.navigate(startScreen) {
                    popUpTo(Screen.Intro.route) { inclusive = true }
                }
                isInitializing = false
            }

            NeuroVerseTheme {
                Scaffold { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Intro.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Intro.route) {
                            IntroScreen(isInitializing)
                        }
                        composable(Screen.Model.route) {
                            ModelsScreen {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Model.route) { inclusive = true }
                                }
                            }
                        }
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onRequestModelChange = {
                                    navController.navigate(Screen.Model.route)
                                },
                                onRequestSettingsChange = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                pluginName = pluginName
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    /** Ensure the encrypted brain structure exists; create it if missing. */
    private fun ensureBrainFileExists() {
        runCatching {
            val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            val brainFile = getBrainFilePath(this@MainActivity)
            if (!brainFile.exists()) {
                val brain = getDefaultBrainStructure()
                saveEncryptedTree(brain, brainFile, key)
            }
        }.onFailure { err ->
            Log.e("MainActivity", "Failed to initialize brain file", err)
        }
    }
}
