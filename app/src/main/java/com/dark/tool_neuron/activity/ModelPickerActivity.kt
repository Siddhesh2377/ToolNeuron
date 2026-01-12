package com.dark.tool_neuron.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.screen.files.ModelPickerScreen
import com.dark.tool_neuron.ui.screen.files.PickerMode
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class ModelPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Surface(Modifier.fillMaxSize()) {
                    ModelPickerScreen(
                        finishWithPath = { absPath, mode ->
                            startActivity(
                                Intent(this, ModelLoadingActivity::class.java).apply {
                                    putExtra(EXTRA_RESULT_FILE_PATH, absPath)
                                    putExtra(EXTRA_PICKER_MODE, mode.name)
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                            finish()
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_FILE_PATH = "model_file_path"
        const val EXTRA_PICKER_MODE = "picker_mode"
    }
}