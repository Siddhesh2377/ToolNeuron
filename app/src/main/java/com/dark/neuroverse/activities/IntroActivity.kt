package com.dark.neuroverse.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
                        if (!Environment.isExternalStorageManager()){
                            val intent = Intent()
                            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }else{
                            CoroutineScope(Dispatchers.IO).launch {
                                if (UserPrefs.isTermsAccepted(context).first()) {
                                    startActivity(Intent(context, MainActivity::class.java))
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

    override fun onResume() {
        super.onResume()
        if (!Environment.isExternalStorageManager()){
            val intent = Intent()
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }else{
            CoroutineScope(Dispatchers.IO).launch {
                if (UserPrefs.isTermsAccepted(this@IntroActivity).first()) {
                    startActivity(Intent(this@IntroActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@IntroActivity, SetUpActivity::class.java))
                }
            }
        }

    }
}