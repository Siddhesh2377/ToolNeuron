package com.dark.neuroverse.compose.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.neuroverse.compose.screens.main.content.BodyChat
import com.dark.neuroverse.compose.screens.main.content.BottomBarMain
import com.dark.neuroverse.compose.screens.main.content.BottomChat
import com.dark.neuroverse.compose.screens.main.content.CarouselExample_MultiBrowse
import com.dark.neuroverse.compose.screens.main.content.HeaderChat
import com.dark.neuroverse.compose.screens.main.content.HeaderMain
import com.dark.neuroverse.compose.screens.main.content.MainCards

@Composable
fun MainScreen(paddingValues: PaddingValues) {

    var action by remember { mutableStateOf(Actions.MAIN) }
    val navController = rememberNavController()

    Column {
        NavHost(
            navController = navController,
            startDestination = action.name
        ) {
            composable(Actions.MAIN.name) {
                MainScreenContent(paddingValues = paddingValues, onAction = {
                    action = it
                    navController.navigate(it.name){
                        launchSingleTop = true
                        popUpTo(Actions.MAIN.name) { inclusive = true }
                    }
                })
            }

            composable(Actions.CHAT.name) {
                ChatScreenContent(paddingValues){
                    navController.navigate(Actions.MAIN.name){
                        launchSingleTop = true
                        popUpTo(Actions.MAIN.name) { inclusive = true }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreenContent(onAction: (Actions) -> Unit, paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 34.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderMain()
        MainCards()
        CarouselExample_MultiBrowse(Modifier.weight(1f))
        BottomBarMain {
            onAction(it)
        }
    }
}

@Composable
fun ChatScreenContent(paddingValues: PaddingValues, onBack: () -> Unit = {}) {
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    var modifier = Modifier
        .fillMaxSize()
        .padding(
            top = paddingValues.calculateTopPadding(),
            bottom = if (!isKeyboardOpen) paddingValues.calculateBottomPadding() else 8.dp,
            start = 24.dp,
            end = 24.dp
        )

    if (isKeyboardOpen) {
        modifier = modifier.imePadding()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderChat(onBack)
        BodyChat(Modifier.weight(1f))
        BottomChat()
    }
}

