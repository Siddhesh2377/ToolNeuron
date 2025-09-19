package com.dark.neuroverse.activity

import android.Manifest
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
import com.dark.userdata.getDefaultBrainStructure
import com.dark.userdata.ntds.getBrainFilePath
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.saveEncryptedTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {
    val permission = Manifest.permission.POST_NOTIFICATIONS
    val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) "Permission denied".makeToast(this)
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            //Init Nav Controller
            val navController = rememberNavController()
            val intent = getIntent()
            val isDirectChatScreen = intent.getBooleanExtra("nav", false)

            //Heavy Work
            LaunchedEffect(Unit) {
                // Request notification permission
                requestNotificationPermission.launch(permission)
                // Ensure the brain file exists
                withContext(Dispatchers.IO) { ensureBrainFileExists() }
            }

            // Navigation logic
            LaunchedEffect(Unit) {
                // Navigate to the intro screen
                if (isDirectChatScreen) {
                    navController.navigate(Screen.Intro.route)
                }else{
                    navController.navigate(Screen.Home.route)
                }

                // Wait for 3 seconds
                delay(3000)

                // Determine the start screen based on whether a model is installed
                navController.navigate(if (ModelManager.isAnyModelInstalled()) Screen.Home.route else Screen.Model.route)
            }

            NeuroVerseTheme {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Intro.route,
                ) {
                    composable(Screen.Intro.route) {
                        IntroScreen()
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
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen()
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

    override fun onDestroy() {
        super.onDestroy()
        ModelManager.shutdown()
    }
}
