package com.dark.neuroverse.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.ai_module.workers.AudioManager
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.Screen
import com.dark.neuroverse.ui.screens.IntroScreen
import com.dark.neuroverse.ui.screens.ModelsScreen
import com.dark.neuroverse.ui.screens.SettingsScreen
import com.dark.neuroverse.ui.screens.home.HomeScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.userdata.getDefaultBrainStructure
import com.dark.neuroverse.userdata.migrateBrainStructure
import com.dark.neuroverse.userdata.ntds.getBrainFilePath
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.loadEncryptedTree
import com.dark.neuroverse.userdata.ntds.saveEncryptedTree
import com.dark.neuroverse.util.makeToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_NAME = "app_preferences"
        private const val KEY_INTRO_SHOWN = "intro_shown"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val INTRO_DURATION_MS = 1500L

        /**
         * Mark setup as completed. Call this from SetupActivity when setup finishes.
         */
        fun markSetupCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            prefs.edit {
                putBoolean(KEY_SETUP_COMPLETED, true)
                apply()
            }
            Log.d("MainActivity", "Setup marked as completed")
        }
    }

    @SuppressLint("InlinedApi")
    private val permission = Manifest.permission.POST_NOTIFICATIONS
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                "Notification permission denied. You may miss important updates.".makeToast(this)
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()
            val intent = intent
            val isDirectChatScreen = intent.getBooleanExtra("nav", false)

            // State for initialization
            var initializationComplete by remember { mutableStateOf(false) }
            var startDestination by remember { mutableStateOf(Screen.Intro.route) }

            // Determine start destination based on app state
            LaunchedEffect(Unit) {
                try {
                    Log.d("MainActivity", "Starting app initialization...")

                    // Request notification permission early
                    requestNotificationPermission.launch(permission)

                    // Perform heavy initialization work
                    withContext(Dispatchers.IO) {
                        ensureBrainFileExists()
                    }

                    // Determine the appropriate start screen
                    startDestination = determineStartDestination(
                        isDirectNavigation = isDirectChatScreen, context = this@MainActivity
                    )

                    Log.d("MainActivity", "Start destination determined: $startDestination")
                    initializationComplete = true

                } catch (e: Exception) {
                    Log.e("MainActivity", "Initialization failed", e)
                    startDestination = Screen.Intro.route
                    initializationComplete = true
                }
            }

            NeuroVerseTheme {
                if (initializationComplete) {
                    NavHost(
                        navController = navController, startDestination = startDestination
                    ) {
                        composable(Screen.Intro.route) {
                            IntroScreen()

                            // Auto-navigate after intro duration
                            LaunchedEffect(Unit) {
                                delay(INTRO_DURATION_MS)

                                val setupCompleted = isSetupCompleted(this@MainActivity)
                                val hasModels = ModelManager.isAnyModelInstalled()

                                val targetDestination = when {
                                    !setupCompleted || !hasModels -> {
                                        // Navigate to SetupActivity
                                        startActivity(
                                            Intent(this@MainActivity, SetupActivity::class.java)
                                        )
                                        finish()
                                        return@LaunchedEffect
                                    }

                                    else -> Screen.Home.route
                                }

                                navController.navigate(targetDestination) {
                                    popUpTo(Screen.Intro.route) { inclusive = true }
                                }

                                // Mark intro as shown for future launches
                                markIntroAsShown(this@MainActivity)
                            }
                        }

                        composable(Screen.Model.route) {
                            ModelsScreen{
                                navController.popBackStack()
                            }
                        }

                        composable(Screen.Home.route) {
                            HomeScreen(onRequestSettingsChange = {
                                navController.navigate(Screen.Settings.route)
                            }, onDataHubClick = {
                                startActivity(
                                    Intent(this@MainActivity, DatahubActivity::class.java)
                                )
                            }, onPluginStoreClick = {
                                startActivity(
                                    Intent(this@MainActivity, PluginHubActivity::class.java)
                                )
                            }, onModelsClick = {
                                navController.navigate(Screen.Model.route)
                            })
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                })
                        }
                    }
                }
            }
        }
    }

    private suspend fun determineStartDestination(
        isDirectNavigation: Boolean, context: Context
    ): String {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Check if this is a direct navigation request
        if (isDirectNavigation) {
            Log.d("MainActivity", "Direct navigation requested -> Home")
            return Screen.Home.route
        }

        // Check if this is the first launch ever
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirstLaunch) {
            Log.d("MainActivity", "First launch detected -> Intro")
            prefs.edit {
                putBoolean(KEY_FIRST_LAUNCH, false)
                apply()
            }
            return Screen.Intro.route
        }

        // Check if intro was shown in this session
        val introShown = prefs.getBoolean(KEY_INTRO_SHOWN, false)
        if (!introShown) {
            Log.d("MainActivity", "Intro not shown recently -> Intro")
            return Screen.Intro.route
        }

        // Skip intro for regular launches
        Log.d("MainActivity", "Regular launch -> Skip intro")
        val setupCompleted = isSetupCompleted(context)
        val hasModels = ModelManager.isAnyModelInstalled()

        return if (setupCompleted && hasModels) {
            Screen.Home.route
        } else {
            // Will be redirected to SetupActivity from Intro
            Screen.Intro.route
        }
    }

    private fun markIntroAsShown(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_INTRO_SHOWN, true)
            apply()
        }
        Log.d("MainActivity", "Intro marked as shown")
    }

    private fun isSetupCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    private fun ensureBrainFileExists() {
        runCatching {
            Log.d("MainActivity", "Checking brain file existence...")

            val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            val brainFile = getBrainFilePath(this@MainActivity)

            if (!brainFile.exists()) {
                Log.d("MainActivity", "Brain file not found, creating default structure")
                val brain = getDefaultBrainStructure()
                saveEncryptedTree(brain, brainFile, key)
                Log.d("MainActivity", "Default brain structure created successfully")
            } else {
                val brain = loadEncryptedTree(brainFile, key) ?: getDefaultBrainStructure()
                migrateBrainStructure(brain.root)
                saveEncryptedTree(brain, brainFile, key)
                Log.d("MainActivity", "Brain file migrated to latest schema")
            }
        }.onFailure { err ->
            Log.e("MainActivity", "Failed to initialize brain file", err)
            runOnUiThread {
                "Failed to initialize app data. Please restart the app.".makeToast(this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "App resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "App paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "App destroyed - shutting down ModelManager")
        ModelManager.shutdown()
        AudioManager.shutdown()
    }

    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_INTRO_SHOWN, false)
            apply()
        }
        Log.d("MainActivity", "App stopped - intro flag reset")
    }
}