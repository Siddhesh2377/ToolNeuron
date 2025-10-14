package com.dark.neuroverse.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
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
import com.dark.neuroverse.userdata.getDefaultBrainStructure
import com.dark.neuroverse.userdata.migrateBrainStructure
import com.dark.neuroverse.userdata.ntds.getBrainFilePath
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.loadEncryptedTree
import com.dark.neuroverse.userdata.ntds.saveEncryptedTree
import com.mp.ai_core.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_NAME = "app_preferences"
        private const val KEY_INTRO_SHOWN = "intro_shown"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val INTRO_DURATION_MS = 1500L // Reduced from 3 seconds
    }

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
                    // Fallback to intro screen on error
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

                                val targetDestination = if (ModelManager.isAnyModelInstalled()) {
                                    Screen.Home.route
                                } else {
                                    Screen.Model.route
                                }

                                navController.navigate(targetDestination) {
                                    popUpTo(Screen.Intro.route) { inclusive = true }
                                }

                                // Mark intro as shown for future launches
                                markIntroAsShown(this@MainActivity)
                            }
                        }

                        composable(Screen.Model.route) {
                            ModelsScreen {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Model.route) { inclusive = true }
                                }
                            }
                        }

                        composable(Screen.Home.route) {
                            HomeScreen(onRequestSettingsChange = {
                                navController.navigate(Screen.Settings.route)
                            }, onDataHubClick = {
                                startActivity(
                                    Intent(
                                        this@MainActivity, DatahubActivity::class.java
                                    )
                                )
                            }, onPluginStoreClick = {
                                startActivity(
                                    Intent(
                                        this@MainActivity, PluginHubActivity::class.java
                                    )
                                )
                            }, onModelsClick = {
                                navController.navigate(Screen.Model.route)
                            })
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                }
                // Show loading/splash content while initializing
                // You could add a proper loading screen component here if needed
            }
        }
    }

    /**
     * Determines the appropriate start destination based on app state and user preferences.
     */
    private suspend fun determineStartDestination(
        isDirectNavigation: Boolean, context: Context
    ): String {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Check if this is a direct navigation request (e.g., from notification)
        if (isDirectNavigation) {
            Log.d("MainActivity", "Direct navigation requested -> Home")
            return Screen.Home.route
        }

        // Check if this is the first launch ever
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirstLaunch) {
            Log.d("MainActivity", "First launch detected -> Intro")
            // Mark first launch as complete
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            return Screen.Intro.route
        }

        // Check if intro was shown in this session/recently
        val introShown = prefs.getBoolean(KEY_INTRO_SHOWN, false)
        if (!introShown) {
            Log.d("MainActivity", "Intro not shown recently -> Intro")
            return Screen.Intro.route
        }

        // Skip intro for regular app launches
        Log.d("MainActivity", "Regular launch -> Skip intro")
        return if (ModelManager.isAnyModelInstalled()) {
            Screen.Home.route
        } else {
            Screen.Model.route
        }
    }

    /**
     * Marks intro as shown for this session/period.
     */
    private fun markIntroAsShown(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INTRO_SHOWN, true).apply()
        Log.d("MainActivity", "Intro marked as shown")
    }

    /**
     * Ensure the encrypted brain structure exists; create it if missing.
     */
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
                // Load and migrate
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
        TtsEngine.tts?.release()
    }

    override fun onStop() {
        super.onStop()
        // Reset intro shown flag when app goes to background
        // This ensures intro shows again after app has been backgrounded for a while
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INTRO_SHOWN, false).apply()
        Log.d("MainActivity", "App stopped - intro flag reset")
    }
}