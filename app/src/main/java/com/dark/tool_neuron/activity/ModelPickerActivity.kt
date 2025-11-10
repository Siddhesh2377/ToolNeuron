package com.dark.tool_neuron.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.screens.picker.ModelPickerScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class GgufPickerActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Surface(Modifier.fillMaxSize()) {
                    ModelPickerScreen(finishWithPath = { absPath ->
                        startActivity(
                            Intent(this, ModelLoadingActivity::class.java).apply {
                                putExtra(EXTRA_RESULT_FILE_PATH, absPath)
                                flags =
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })

                        finish()
                    }, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_FILE_PATH = "gguf_file_path"
    }
}


