package com.dark.neuroverse.compose.screens.setup

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.neuroverse.activities.MainActivity
import com.dark.neuroverse.compose.screens.setup.intro.IntroScreen
import com.dark.neuroverse.compose.screens.setup.permissions.PermissionScreen
import com.dark.neuroverse.utils.UserPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun SetUpScreen(paddingValues: PaddingValues) {
    var showLoading by remember { mutableStateOf(false) }
    var closeLoadingScreen by remember { mutableStateOf(false) }
    val animDuration = 500
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(700)
        showLoading = true
        delay(2700)

        UserPrefs.isOnboardingComplete(context).collect {
            if (it) {
                Intent(context, MainActivity::class.java).apply {
                    context.startActivity(this)
                }
            } else {
                closeLoadingScreen = true
            }
        }
    }

    AnimatedContent(closeLoadingScreen, transitionSpec = {
        (fadeIn(animationSpec = tween(durationMillis = animDuration)) +
                scaleIn(animationSpec = tween(durationMillis = animDuration)))
            .togetherWith(
                fadeOut(animationSpec = tween(durationMillis = animDuration)) +
                        scaleOut(animationSpec = tween(durationMillis = animDuration))
            )
    }, label = "setup") {
        when (it) {
            true -> SetUpCompose(paddingValues)
            false -> IntroScreen(showLoading)
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
                scope.launch {
                    UserPrefs.setOnboardingComplete(context, true)
                    Intent(context, MainActivity::class.java).apply {
                        context.startActivity(this)
                    }
                }
            }
        }
    }
}