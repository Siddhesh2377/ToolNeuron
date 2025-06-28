package com.dark.neuroverse.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.data.UserData
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.userdata.collectAllNodes
import com.dark.userdata.getBrainFilePath
import com.dark.userdata.getOrCreateHardwareBackedAesKey
import com.dark.userdata.loadEncryptedTree
import com.dark.userdata.neuron_tree.NeuronNode
import com.dark.userdata.neuron_tree.NeuronTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File

class BrainViewerActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Brain Viewer") })
                }) { padding ->
                    BrainScreen(padding)
                }
            }
        }
    }
}

@Composable
fun BrainScreen(paddingValues: PaddingValues) {

    val context = LocalContext.current
    var nodeList by remember { mutableStateOf<List<NeuronNode>>(emptyList()) }
    val brainFile = getBrainFilePath(context)

    LaunchedEffect(Unit) {
        val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
        val nodeTree: NeuronTree? = loadEncryptedTree(brainFile, key)

        nodeTree?.collectAllNodes()?.let {
            nodeList = it
            it.forEach { node ->
                Log.d("NeuronTree", "ID=${node.id}, Data=${node.data.content}, Type=${node.data.type}")
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Opened File:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(brainFile.canonicalPath, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        if (nodeList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data found or failed to decrypt.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(nodeList) { node ->
                    Card(
                        Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("ID: ${node.id}", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Content: ${node.data.content}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text("Type: ${node.data.type}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(2.dp))
                            Text("Children: ${node.getChildNodes().size}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
