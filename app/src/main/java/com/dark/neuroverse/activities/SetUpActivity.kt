package com.dark.neuroverse.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dark.neuroverse.compose.screens.setup.common.TopBar
import com.dark.neuroverse.compose.screens.setup.data.TermsAndConditionScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.utils.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.dark.neuroverse.compose.screens.setup.data.SetupState

class SetUpActivity : ComponentActivity()  {
    @OptIn(
        ExperimentalMaterial3Api::class,
        ExperimentalMaterial3ExpressiveApi::class,
        ExperimentalAnimationApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            enableEdgeToEdge()
            NeuroVerseTheme {
                val context = LocalContext.current
                var setupState by remember { mutableStateOf(SetupState.TERMS) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    val termsAccepted = UserPrefs.isTermsAccepted(context).first()
                    setupState = if (!termsAccepted) SetupState.TERMS else SetupState.COMPLETED
                }

                Scaffold { padding ->
                    Column(Modifier.padding(padding)) {
                        TopBar(
                            title = "Quick Setup..!",
                            subtitle = "Let's Go Through \nSome Terms....!"
                        )

                        AnimatedContent(
                            targetState = setupState,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { state ->
                            when (state) {
                                SetupState.TERMS -> TermsAndConditionScreen {
                                    scope.launch(Dispatchers.IO) {
                                        //UserPrefs.setTermsAccepted(context, true)
                                        //setupState = SetupState.COMPLETED
                                    }
                                }

                                SetupState.COMPLETED -> launchMain()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
