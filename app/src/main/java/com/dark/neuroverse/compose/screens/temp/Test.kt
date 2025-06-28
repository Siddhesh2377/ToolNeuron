package com.dark.neuroverse.compose.screens.temp

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.activities.BrainViewerActivity
import com.dark.userdata.getOrCreateHardwareBackedAesKey
import com.dark.userdata.memoryMapFile
import com.dark.userdata.neuron_tree.NeuronNode
import com.dark.userdata.neuron_tree.NeuronTree
import com.dark.userdata.neuron_tree.NodeData
import com.dark.userdata.neuron_tree.NodeType
import com.dark.userdata.saveEncryptedTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.system.measureTimeMillis

@Composable
fun NeuronTreeScreen(paddingValues: PaddingValues) {
    var addTime by remember { mutableStateOf<Long?>(null) }
    var saveTime by remember { mutableStateOf<Long?>(null) }
    var loadTime by remember { mutableStateOf<Long?>(null) }
    var currentProgress by remember { mutableStateOf("Idle") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val brainFile = File(context.filesDir, "secure_brain.brain")
    val keyAlias = "brain_key"

    Column(Modifier.padding(paddingValues)) {
//
//        Button(onClick = {
//            scope.launch(Dispatchers.Default) {
//                currentProgress = "Generating NeuronTree..."
//
//                val key = getOrCreateHardwareBackedAesKey(keyAlias)
//
//                val root = NeuronNode("root", NodeData("Root Node Data", NodeType.ROOT))
//                val tree = NeuronTree(root)
//
//                val totalNodes = 10  // Adjust for your testing
//
//                val add = measureTimeMillis {
//                    repeat(totalNodes) { index ->
//                        if (index % 100 == 0) {
//                            currentProgress = "Adding Node $index"
//                        }
//
//                        val node = if (index == 20) {
//                            NeuronNode(
//                                "nodeU",
//                                NodeData(
//                                    "Unique Special Node with ID=${UUID.randomUUID()}",
//                                    NodeType.HOLDER
//                                )
//                            )
//                        } else {
//                            NeuronNode(
//                                "node-$index",
//                                NodeData(generateRandomData(128), randomNodeType())
//                            )
//                        }
//
//                        tree.addChild("root", node)
//                    }
//                }
//
//                addTime = add
//
//                currentProgress = "Encrypting & Saving..."
//                val save = measureTimeMillis {
//                    saveEncryptedTree(tree, brainFile, key)
//                }
//                saveTime = save
//
//                currentProgress = "Memory Mapping File..."
//                val load = measureTimeMillis {
//                    memoryMapFile(brainFile)
//                }
//                loadTime = load
//
//                currentProgress = "Completed"
//            }
//
//        }) {
//            Text("Start NeuronTree Test")
//        }

        Spacer(Modifier.height(16.dp))

        Text("Progress: $currentProgress")
        Text("Time to add nodes: ${addTime ?: "-"} ms")
        Text("Time to save encrypted tree: ${saveTime ?: "-"} ms")
        Text("Time to memory map file: ${loadTime ?: "-"} ms")

        Button(onClick = {
            val intent = Intent(context, BrainViewerActivity::class.java)
            intent.putExtra("brain_file_path", brainFile.absolutePath)
            intent.putExtra("brain_key_alias", keyAlias)
            context.startActivity(intent)
        }) {
            Text("View Brain")
        }
    }
}

fun generateRandomData(length: Int): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun randomNodeType(): NodeType {
    val types = NodeType.entries.filter { it != NodeType.ROOT }
    return types.random()
}

