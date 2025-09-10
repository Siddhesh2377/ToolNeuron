package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.ui.screens.NeuronTreeScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.readBrainFile
import kotlinx.coroutines.flow.MutableStateFlow

class TempActivity : ComponentActivity() {

    private val key = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
    private val rootNode = MutableStateFlow<NeuronTree?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize NeuronTree
        key.value = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
        rootNode.value = readBrainFile(key.value, this)
        rootNode.value?.printTree()

        setContent {
            NeuroVerseTheme {
                val tree by rootNode.collectAsState()
                tree?.let { tree ->
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        "Your Brain Map",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },)
                        }) { padding ->
                        NeuronTreeScreen(
                            Modifier.padding(padding),
                            tree
                        )   // 👈 the creative visualization
                    }
                }
            }
        }
    }
}


