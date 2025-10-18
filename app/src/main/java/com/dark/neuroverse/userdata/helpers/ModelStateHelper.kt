package com.dark.neuroverse.userdata.helpers

import android.content.Context
import android.util.Log
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronNode
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeData
import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeType
import org.json.JSONObject
import java.io.File

private const val TAG = "ModelStateHelper"

/**
 * Model State Structure:
 * root
 *  └── modelState (OPERATOR)
 *       ├── <chatId1> (STEAM)
 *       │    └── content: {"stateFile": "path/to/state.bin", "size": 123456, "timestamp": 1234567890}
 *       ├── <chatId2> (STEAM)
 *       └── ...
 */
object ModelStateHelper {

    /**
     * Get the modelState operator node
     */
    private fun getModelStateNode(root: NeuronNode): NeuronNode {
        val tree = NeuronTree(root)
        return tree.getNodeDirectOrNull("modelSate")
            ?: NeuronNode("modelSate", NodeData("", NodeType.OPERATOR)).also {
                tree.addChild(root.id, it)
            }
    }

    /**
     * Get state directory for storing .state files
     */
    private fun getStateDirectory(context: Context): File {
        return File(context.filesDir, "model_states").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Get state file path for a specific chat
     */
    private fun getStateFilePath(context: Context, chatId: String): String {
        return File(getStateDirectory(context), "$chatId.state").absolutePath
    }

    /**
     * Create or update model state entry for a chat
     */
    fun createOrUpdateModelState(
        root: NeuronNode,
        context: Context,
        chatId: String,
        stateSize: Long = 0L
    ): NeuronNode {
        val modelStateNode = getModelStateNode(root)
        val tree = NeuronTree(root)

        // Check if state already exists for this chat
        val existingNode = tree.getNodeDirectOrNull(chatId)

        val stateFilePath = getStateFilePath(context, chatId)
        val stateData = JSONObject().apply {
            put("stateFile", stateFilePath)
            put("size", stateSize)
            put("timestamp", System.currentTimeMillis())
            put("chatId", chatId)
        }

        return if (existingNode != null && existingNode.data.type == NodeType.STEAM) {
            // Update existing node
            existingNode.data.content = stateData.toString()
            Log.d(TAG, "Updated model state for chat: $chatId")
            existingNode
        } else {
            // Create new node
            val newStateNode = NeuronNode(
                id = chatId,
                data = NodeData(stateData.toString(), NodeType.STEAM)
            )
            tree.addChild(modelStateNode.id, newStateNode)
            Log.d(TAG, "Created new model state for chat: $chatId")
            newStateNode
        }
    }

    /**
     * Get model state info for a specific chat
     */
    fun getModelState(root: NeuronNode, chatId: String): ModelStateInfo? {
        val tree = NeuronTree(root)
        val stateNode = tree.getNodeDirectOrNull(chatId) ?: return null

        return try {
            val json = JSONObject(stateNode.data.content)
            ModelStateInfo(
                chatId = json.optString("chatId", chatId),
                stateFilePath = json.getString("stateFile"),
                size = json.getLong("size"),
                timestamp = json.getLong("timestamp")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing model state for chat: $chatId", e)
            null
        }
    }

    /**
     * Check if a chat has saved model state
     */
    fun hasModelState(root: NeuronNode, chatId: String): Boolean {
        val stateInfo = getModelState(root, chatId) ?: return false
        val stateFile = File(stateInfo.stateFilePath)
        return stateFile.exists() && stateFile.length() > 0
    }

    /**
     * Delete model state for a specific chat
     */
    fun deleteModelState(root: NeuronNode, chatId: String): Boolean {
        return try {
            val stateInfo = getModelState(root, chatId)
            val tree = NeuronTree(root)

            // Delete the physical file
            var fileDeleted = false
            if (stateInfo != null) {
                val stateFile = File(stateInfo.stateFilePath)
                fileDeleted = stateFile.delete()
                Log.d(TAG, "State file deleted: $fileDeleted for chat: $chatId")
            }

            // Remove node from tree
            val modelStateNode = getModelStateNode(root)
            val removed = tree.deleteNodeById(chatId)

            Log.d(TAG, "Model state deleted for chat: $chatId (file: $fileDeleted, node: $removed)")
            fileDeleted || removed
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model state for chat: $chatId", e)
            false
        }
    }

    /**
     * Get all chat IDs that have model states
     */
    fun getAllModelStates(root: NeuronNode): List<ModelStateInfo> {
        val modelStateNode = getModelStateNode(root)
        return modelStateNode.children.mapNotNull { node ->
            try {
                val json = JSONObject(node.data.content)
                ModelStateInfo(
                    chatId = node.id,
                    stateFilePath = json.getString("stateFile"),
                    size = json.getLong("size"),
                    timestamp = json.getLong("timestamp")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing model state: ${node.id}", e)
                null
            }
        }
    }

    /**
     * Get total size of all model states
     */
    fun getTotalStateSize(root: NeuronNode): Long {
        return getAllModelStates(root).sumOf { it.size }
    }

    /**
     * Clean up orphaned state files (files without tree entries)
     */
    fun cleanupOrphanedStates(root: NeuronNode, context: Context): Int {
        val stateDir = getStateDirectory(context)
        val allStates = getAllModelStates(root).map { File(it.stateFilePath).name }.toSet()

        var deletedCount = 0
        stateDir.listFiles()?.forEach { file ->
            if (file.extension == "state" && file.name !in allStates) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted orphaned state file: ${file.name}")
                }
            }
        }

        Log.i(TAG, "Cleaned up $deletedCount orphaned state files")
        return deletedCount
    }

    /**
     * Clean up old states (keep only N most recent)
     */
    fun cleanupOldStates(
        root: NeuronNode,
        maxStates: Int = 10
    ): Int {
        val allStates = getAllModelStates(root)
            .sortedByDescending { it.timestamp }

        if (allStates.size <= maxStates) {
            Log.d(TAG, "No cleanup needed, current states: ${allStates.size}")
            return 0
        }

        val statesToDelete = allStates.drop(maxStates)
        var deletedCount = 0

        statesToDelete.forEach { state ->
            if (deleteModelState(root, state.chatId)) {
                deletedCount++
            }
        }

        Log.i(TAG, "Cleaned up $deletedCount old states")
        return deletedCount
    }

    /**
     * Clean up large states (exceeding size limit)
     */
    fun cleanupLargeStates(
        root: NeuronNode,
        maxSizeBytes: Long = 50 * 1024 * 1024 // 50 MB per state
    ): Int {
        val allStates = getAllModelStates(root)
        var deletedCount = 0

        allStates.filter { it.size > maxSizeBytes }.forEach { state ->
            if (deleteModelState(root, state.chatId)) {
                deletedCount++
                Log.d(TAG, "Deleted large state: ${state.chatId} (${state.size / 1024 / 1024} MB)")
            }
        }

        Log.i(TAG, "Cleaned up $deletedCount large states")
        return deletedCount
    }

    /**
     * Verify state file integrity
     */
    fun verifyStateIntegrity(root: NeuronNode, context: Context, chatId: String): Boolean {
        val stateInfo = getModelState(root, chatId) ?: return false
        val stateFile = File(stateInfo.stateFilePath)

        return stateFile.exists() && stateFile.length() == stateInfo.size
    }

    /**
     * Get state statistics
     */
    fun getStateStatistics(root: NeuronNode, context: Context): StateStatistics {
        val allStates = getAllModelStates(root)
        val totalSize = allStates.sumOf { it.size }
        val validStates = allStates.count {
            File(it.stateFilePath).exists()
        }

        return StateStatistics(
            totalStates = allStates.size,
            validStates = validStates,
            totalSizeBytes = totalSize,
            averageSizeBytes = if (allStates.isNotEmpty()) totalSize / allStates.size else 0L,
            oldestTimestamp = allStates.minOfOrNull { it.timestamp } ?: 0L,
            newestTimestamp = allStates.maxOfOrNull { it.timestamp } ?: 0L
        )
    }

    /**
     * Update state size after save
     */
    fun updateStateSize(root: NeuronNode, context: Context, chatId: String): Boolean {
        val stateFilePath = getStateFilePath(context, chatId)
        val stateFile = File(stateFilePath)

        if (!stateFile.exists()) {
            Log.w(TAG, "State file does not exist: $stateFilePath")
            return false
        }

        val actualSize = stateFile.length()
        createOrUpdateModelState(root, context, chatId, actualSize)
        return true
    }

    /**
     * Export state info as JSON for debugging
     */
    fun exportStateInfo(root: NeuronNode): JSONObject {
        val allStates = getAllModelStates(root)
        val statesArray = org.json.JSONArray()

        allStates.forEach { state ->
            statesArray.put(JSONObject().apply {
                put("chatId", state.chatId)
                put("stateFile", state.stateFilePath)
                put("size", state.size)
                put("sizeMB", "%.2f".format(state.size / 1024.0 / 1024.0))
                put("timestamp", state.timestamp)
            })
        }

        return JSONObject().apply {
            put("totalStates", allStates.size)
            put("totalSize", allStates.sumOf { it.size })
            put("states", statesArray)
        }
    }
}

/**
 * Data class representing model state information
 */
data class ModelStateInfo(
    val chatId: String,
    val stateFilePath: String,
    val size: Long,
    val timestamp: Long
)

/**
 * Statistics about all model states
 */
data class StateStatistics(
    val totalStates: Int,
    val validStates: Int,
    val totalSizeBytes: Long,
    val averageSizeBytes: Long,
    val oldestTimestamp: Long,
    val newestTimestamp: Long
) {
    val totalSizeMB: Double
        get() = totalSizeBytes / 1024.0 / 1024.0

    val averageSizeMB: Double
        get() = averageSizeBytes / 1024.0 / 1024.0
}