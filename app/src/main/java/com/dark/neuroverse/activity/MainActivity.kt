package com.dark.neuroverse.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permission = Manifest.permission.POST_NOTIFICATIONS
        val requestNotificationPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) "Permission denied".makeToast(this@MainActivity)
            }

        CoroutineScope(Dispatchers.IO).launch {
            val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            val brainFile = getBrainFilePath(this@MainActivity)

            if (!brainFile.exists()) {
                val brain = getDefaultBrainStructure()
                saveEncryptedTree(brain, brainFile, key)
            }
        }

        setContent {
            val navController = rememberNavController()

            var isJNIReady by remember { mutableStateOf(false) }
            var isJNIDownloading by remember { mutableStateOf(false) }

            //ASK FOR NOTIFICATION PERMISSION
            requestNotificationPermission.launch(permission)

            // Initial JNI + model check
            LaunchedEffect(Unit) {
                isJNIDownloading = !JNILibHelper.checkIfJNILibExists(this@MainActivity)

                loadJNI {
                    isJNIDownloading = false
                    isJNIReady = true
                }
            }

            // Navigate once JNI is ready
            LaunchedEffect(isJNIReady) {
                if (isJNIReady) {
                    val startScreen = if (ModelManager.isAnyModelInstalled()) Screen.Home.route
                    else Screen.Model.route

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
                            IntroScreen(isJNIDownloading)
                        }

                        composable(Screen.Model.route) {
                            ModelsScreen(
                                onNext = {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Model.route) { inclusive = true }
                                    }
                                })
                        }

                        composable(Screen.Home.route) {
                            HomeScreen(onRequestModelChange = {
                                navController.navigate(Screen.Model.route)
                            }, onRequestSettingsChange = {
                                navController.navigate(Screen.Settings.route)
                            })
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
        JNILibHelper.loadJNILib(this@MainActivity) {
            CoroutineScope(Dispatchers.IO).launch {
                val model = ModelManager.getFirstModel()
                if (model != null) {
                    ModelManager.loadModel(this@MainActivity, model) {
                        onLoaded()
                        return@loadModel
                    }
                }

                onLoaded()
            }
        }
    }
}

