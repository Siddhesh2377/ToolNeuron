package com.dark.userdata.ntds

import android.content.Context
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import javax.crypto.SecretKey

// Get secure brain file path
fun getBrainFilePath(context: Context): File {
    return File(context.filesDir, "secure_brain.brain")
}

// Flatten entire tree into List<Node> (useful for display)
fun NeuronTree.collectAllNodes(): List<NeuronNode> {
    val result = mutableListOf<NeuronNode>()
    fun traverse(node: NeuronNode) {
        result.add(node)
        node.children.forEach { traverse(it) }
    }
    traverse(root)
    return result
}

// Memory-map file for fast read
fun memoryMapFile(file: File): MappedByteBuffer {
    val raf = RandomAccessFile(file, "r")
    return raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
}

// Save tree as JSON, encrypt, store to file
fun saveEncryptedTree(tree: NeuronTree, file: File, key: SecretKey) {
    val jsonData = Json.encodeToString(tree.root)
    val encrypted = encrypt(jsonData.toByteArray(Charsets.UTF_8), key)
    Files.write(file.toPath(), encrypted)
}

// Load and decrypt tree from file
fun loadEncryptedTree(file: File, key: SecretKey): NeuronTree? {
    if (!file.exists()) return null
    val mapped = memoryMapFile(file)
    val encrypted = ByteArray(mapped.capacity()).apply { mapped.get(this) }
    val decrypted = decrypt(encrypted, key)
    val jsonString = decrypted.toString(Charsets.UTF_8)

    val rootNode = Json.decodeFromString<NeuronNode>(jsonString)
    return NeuronTree(rootNode)
}
