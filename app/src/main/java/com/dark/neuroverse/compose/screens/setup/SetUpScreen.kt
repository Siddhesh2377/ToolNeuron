package com.dark.neuroverse.compose.screens.setup

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.activities.MainActivity
import com.dark.neuroverse.compose.screens.setup.data.TermsAndConditionScreen
import com.dark.neuroverse.compose.screens.setup.intro.IntroScreen
import com.dark.neuroverse.compose.screens.setup.permissions.PermissionScreen
import com.dark.neuroverse.utils.UserPrefs
import com.dark.userdata.getBrainFilePath
import com.dark.userdata.getOrCreateHardwareBackedAesKey
import com.dark.userdata.neuron_tree.getDefaultBrainStructure
import com.dark.userdata.saveEncryptedTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun SetUpScreen(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val key = remember { getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS) }
    val brainFile = remember { getBrainFilePath(context) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(SetupState.FRESH_START) }

    LaunchedEffect(Unit) {
        if (!brainFile.exists()) {
            val brain = getDefaultBrainStructure()
            saveEncryptedTree(brain, brainFile, key)
        }

        delay(700)

        UserPrefs.isTermsAccepted(context).collect { termsAccepted ->
            state = if (termsAccepted) {
                val onboardingComplete = UserPrefs.isOnboardingComplete(context).first()
                if (onboardingComplete) SetupState.COMPLETED else SetupState.PERMISSIONS
            } else {
                SetupState.TERMS_AND_CONDITIONS
            }
        }
    }

    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith  fadeOut() },
        label = "setup"
    ) { currentState ->
        when (currentState) {
            SetupState.FRESH_START -> IntroScreen(showLoading = false)

            SetupState.TERMS_AND_CONDITIONS -> TermsAndConditionScreen {
                scope.launch(Dispatchers.IO) {
                    UserPrefs.setTermsAccepted(context, true)
                }
            }

            SetupState.PERMISSIONS -> SetUpCompose(paddingValues)

            SetupState.COMPLETED -> {
                context.startActivity(Intent(context, MainActivity::class.java))
            }
        }
    }
}

@Composable
fun SetUpCompose(paddingValues: PaddingValues) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = "permission_screen"
    ) {
        composable("permission_screen") {
            PermissionScreen(paddingValues) {
                scope.launch(Dispatchers.IO) {
                    UserPrefs.setOnboardingComplete(context, true)
                    context.startActivity(Intent(context, MainActivity::class.java))
                }
            }
        }
    }
}

enum class SetupState {
    FRESH_START,
    TERMS_AND_CONDITIONS,
    PERMISSIONS,
    COMPLETED
}
