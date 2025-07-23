package com.dark.neuroverse.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.dark.ai_module.helpers.JNILibHelper
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
import com.mp.updatemanager.UpdateActionReceiver
import com.mp.updatemanager.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(activityJob + Dispatchers.IO)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permission = Manifest.permission.POST_NOTIFICATIONS
        val requestNotificationPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) "Permission denied".makeToast(this)
            }

        // Save brain structure in IO scope
        activityScope.launch {
            val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            val brainFile = getBrainFilePath(this@MainActivity)

            if (!brainFile.exists()) {
                val brain = getDefaultBrainStructure()
                saveEncryptedTree(brain, brainFile, key)
                cancel()
            }
        }

        setContent {
            val navController = rememberNavController()
            var isJNIReady by remember { mutableStateOf(false) }
            var isJNIDownloading by remember { mutableStateOf(false) }

            requestNotificationPermission.launch(permission)

            LaunchedEffect(Unit) {
                isJNIDownloading = !JNILibHelper.checkIfJNILibExists(this@MainActivity)

                // Run JNI + model load
                withContext(Dispatchers.IO) {
                    loadJNI {
                        isJNIDownloading = false
                        isJNIReady = true
                        cancel()
                    }
                }
            }

            LaunchedEffect(isJNIReady) {
                if (isJNIReady) {
                    val startScreen = if (ModelManager.isAnyModelInstalled())
                        Screen.Home.route else Screen.Model.route
                    navController.navigate(startScreen) {
                        popUpTo(Screen.Intro.route) { inclusive = true }
                    }
                }
            }

            NeuroVerseTheme {
                Scaffold { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Intro.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Intro.route) {
                            Log.d("Screen", "Intro")
                            IntroScreen(isJNIDownloading)
                        }
                        composable(Screen.Model.route) {
                            Log.d("Screen", "Model")
                            ModelsScreen {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Model.route) { inclusive = true }
                                }
                            }
                        }
                        composable(Screen.Home.route) {
                            Log.d("Screen", "Home")
                            HomeScreen(
                                onRequestModelChange = {
                                    navController.navigate(Screen.Model.route)
                                },
                                onRequestSettingsChange = {
                                    navController.navigate(Screen.Settings.route)
                                }
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

    private suspend fun loadJNI(onLoaded: () -> Unit) {
        val model = ModelManager.getFirstModel()
        JNILibHelper.loadJNILib(this) {
            if (model != null) {
                ModelManager.loadModel(this, model) {
                    onLoaded()
                }
            } else {
                onLoaded()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}


