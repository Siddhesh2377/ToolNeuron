package com.dark.neuroverse.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dark.neuroverse.compose.screens.setup.intro.IntroScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.utils.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IntroActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this

            enableEdgeToEdge()
            NeuroVerseTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) {
                    IntroScreen(it) {
                        CoroutineScope(Dispatchers.IO).launch {
                            if (UserPrefs.isTermsAccepted(context).first()) {
                                startActivity(Intent(context, SetUpActivity::class.java))
                            } else {
                                startActivity(Intent(context, SetUpActivity::class.java))
                            }
                        }
                    }
                }
            }
        }
    }
}