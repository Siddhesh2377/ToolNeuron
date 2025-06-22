package com.dark.neuroverse.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.core.view.WindowCompat
import com.dark.neuroverse.compose.screens.home.HomeScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.google.firebase.FirebaseApp


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.surface) { it ->

//                    NeuroVScreen(onClickOutside = {
//                        PluginManager.loadSTTPlugins()
//                    })

                    //NeuronDemoScreen(it)

                    // ChatScreen(it)
                    //STTScreen(it)

                    HomeScreen(it)
                    // runPluginInSandbox(this, "List Applications Plugin")

                }
            }
        }
    }

}



