package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.BuildConfig
import com.dark.tool_neuron.model.ChatList
import com.dark.tool_neuron.userdata.getDefaultChatHistory
import com.dark.tool_neuron.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NeuronNode
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NeuronTree
import com.dark.tool_neuron.userdata.readBrainFile
import com.dark.tool_neuron.userdata.saveTree
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/**
 * Manages user data persistence and brain file operations.
 * Handles encryption, tree structure, and chat list caching.
 */
object UserDataManager {
    private const val TAG = "UserDataManager"

    private val encryptionKey = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)

    private val _rootNode = MutableStateFlow(NeuronNode())

    /**
     * Initializes the manager by loading the brain file from disk.
     * Should be called during app startup.
     */
    fun init(appContext: Context) {
        try {
            _rootNode.value = readBrainFile(encryptionKey, appContext).root
            Log.d(TAG, "Brain file loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading brain file", e)
            UIStateManager.setStateError("Failed to load user data", cause = e)
        }
    }

    /**
     * Refreshes the chat list from disk and invokes callbacks.
     *
     * @param onLoaded Called with the fresh chat list
     * @param onError Called if an error occurs
     */
    fun refreshChatListFromDisk(
        onLoaded: (List<ChatList>) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        try {
            val history = getDefaultChatHistory(_rootNode.value)

            val freshList = history.children
                .filter { it.data.content.isNotBlank() }
                .mapNotNull { node ->
                    try {
                        val json = JSONObject(node.data.content)
                        val title = json.optString("title", "Untitled")
                        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        ChatList(node.id, title, timestamp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chat node ${node.id}", e)
                        null
                    }
                }
                .sortedByDescending { it.timestamp }

            Log.d(TAG, "Chat list refreshed: ${freshList.size} chats")
            onLoaded(freshList)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing chat list", e)
            onError(e)
        }
    }

    /**
     * Gets the current root node.
     */
    fun getRootNode(): NeuronNode {
        return _rootNode.value
    }

    /**
     * Saves the entire tree structure to disk.
     * Also refreshes the chat list after saving.
     *
     * @param appContext Application context
     */
    fun performTreeSave(appContext: Context) {
        try {
            saveTree(NeuronTree(_rootNode.value), appContext, BuildConfig.ALIAS)
            // **Reload fresh root after write** so in‑memory view matches disk
            reloadBrainFile(appContext)
            Log.d(TAG, "root reloaded from disk after save")
        } catch (e: Exception) {
            Log.e(TAG, "Tree save failed", e)
            UIStateManager.setStateError("Tree save failed", cause = e)
        } finally {
            // CRITICAL: Always refresh chat list after tree save
            refreshChatListFromDisk(
                onError = { Log.e(TAG, "Error refreshing chat list after save", it) }
            )
        }
    }

    /**
     * Reloads the brain file from disk.
     * Useful for recovering from corruption or manual file updates.
     */
    fun reloadBrainFile(appContext: Context) {
        try {
            Log.d(TAG, "Reloading brain file...")
            _rootNode.value = readBrainFile(encryptionKey, appContext).root
            Log.d(TAG, "Brain file reloaded successfully")

            // Refresh chat list with new data
            refreshChatListFromDisk()
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading brain file", e)
            UIStateManager.setStateError("Failed to reload user data", cause = e)
        }
    }

    /**
     * Gets the encryption key used for the brain file.
     * Useful for debugging or manual operations.
     */
    fun getEncryptionKey() = encryptionKey
}