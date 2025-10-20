package com.dark.neuroverse.userdata.ntds

import android.content.Context
import android.util.Log
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronNode
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import javax.crypto.SecretKey

private const val TAG = "NRTS_Utils"

/**
 * Get the absolute path to the encrypted brain file.
 */
fun getBrainFilePath(context: Context): File {
    val file = File(context.filesDir, "secure_brain.brain")
    Log.d(TAG, "Brain file path resolved: ${file.absolutePath}")
    return file
}

/**
 * Flatten the entire `NeuronTree` into a flat `List`.
 * Useful for debugging / UI displays.
 */
fun NeuronTree.collectAllNodes(): List<NeuronNode> {
    val result = mutableListOf<NeuronNode>()
    fun traverse(node: NeuronNode) {
        result.add(node)
        node.children.forEach { traverse(it) }
    }
    traverse(root)
    Log.d(TAG, "Collected ${result.size} nodes from the tree")
    return result
}

/**
 * Memory‑map a file for fast read‑only access.
 */
fun memoryMapFile(file: File): MappedByteBuffer {
    Log.d(TAG, "Mapping file into memory: ${file.absolutePath}")
    val raf = RandomAccessFile(file, "r")
    val buffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    Log.d(TAG, "Memory map created: ${buffer.capacity()} bytes")
    return buffer
}

/**
 * Persist a `NeuronTree` to a file. The tree is first
 * serialised to JSON, encrypted with the supplied key,
 * then written via `java.nio.Files`.
 */
fun saveEncryptedTree(tree: NeuronTree, file: File, key: SecretKey) {
    try {
        Log.d(TAG, "Serialising tree for encryption")
        val jsonData = Json.encodeToString(tree.root)

        Log.d(TAG, "Encrypting data")
        val encrypted = encrypt(jsonData.toByteArray(Charsets.UTF_8), key)

        Log.d(TAG, "Writing encrypted data to ${file.absolutePath}")
        Files.write(file.toPath(), encrypted)

        Log.d(TAG, "Tree successfully persisted (${encrypted.size} bytes)")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to persist tree", e)
        throw e
    }
}

/**
 * Load and decrypt a tree from disk.
 *
 * @return A `NeuronTree` if the file exists and decryption succeeds; `null` otherwise.
 */
fun loadEncryptedTree(file: File, key: SecretKey): NeuronTree? {
    if (!file.exists()) {
        Log.w(TAG, "Brain file not found: ${file.absolutePath}")
        return null
    }

    return try {
        Log.d(TAG, "Loading encrypted file: ${file.absolutePath}")
        val mapped = memoryMapFile(file)

        val encrypted = ByteArray(mapped.capacity()).apply { mapped.get(this) }
        Log.d(TAG, "Encrypted payload size: ${encrypted.size} bytes")

        Log.d(TAG, "Decrypting payload")
        val decrypted = decrypt(encrypted, key)
        val jsonString = decrypted.toString(Charsets.UTF_8)

        Log.d(TAG, "Decoding JSON to NeuronNode")
        val rootNode = Json.decodeFromString<NeuronNode>(jsonString)

        Log.d(TAG, "Tree successfully loaded from disk")
        NeuronTree(rootNode)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load or decrypt brain file", e)
        null
    }
}