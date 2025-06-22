package com.dark.neuroverse.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dark.neuroverse.compose.screens.setup.SetUpScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme

class SetUpActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            enableEdgeToEdge()
            NeuroVerseTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.surface) {
                    SetUpScreen(it)
                }
            }
        }
    }
}