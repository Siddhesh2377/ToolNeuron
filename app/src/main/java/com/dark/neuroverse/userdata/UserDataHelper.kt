package com.dark.neuroverse.userdata

import android.content.Context
import com.dark.neuroverse.userdata.helpers.MemoryDataTags
import com.dark.neuroverse.userdata.helpers.createNewMemory
import com.dark.neuroverse.userdata.ntds.getBrainFilePath
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.loadEncryptedTree
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronNode
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeData
import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeType
import com.dark.neuroverse.userdata.ntds.saveEncryptedTree
import org.json.JSONObject
import javax.crypto.SecretKey

fun getDefaultBrainStructure(): NeuronTree {
    val root = NeuronNode("root", NodeData("", NodeType.ROOT))
    val tree = NeuronTree(root)

    val chatHistory = NeuronNode("chatHistory", NodeData("", NodeType.OPERATOR))
    val memoryHistory = NeuronNode("memoryHistory", NodeData("", NodeType.OPERATOR))
    val modelState = NeuronNode("modelSate", NodeData("", NodeType.OPERATOR))

    tree.addChild(root.id, chatHistory, memoryHistory, modelState)

    createNewMemory(root, MemoryDataTags.Family, JSONObject())
    createNewMemory(root, MemoryDataTags.Friends, JSONObject())
    createNewMemory(root, MemoryDataTags.Work, JSONObject())
    createNewMemory(root, MemoryDataTags.Health, JSONObject())
    createNewMemory(root, MemoryDataTags.Education, JSONObject())
    createNewMemory(root, MemoryDataTags.Entertainment, JSONObject())
    createNewMemory(root, MemoryDataTags.Other, JSONObject())
    return tree
}

fun migrateBrainStructure(root: NeuronNode) {
    val tree = NeuronTree(root)

    // Ensure chat + memory operators exist
    val chatHistory = tree.getNodeDirectOrNull("chatHistory")
        ?: NeuronNode("chatHistory", NodeData("", NodeType.OPERATOR)).also {
            tree.addChild(root.id, it)
        }

    val memoryHistory = tree.getNodeDirectOrNull("memoryHistory")
        ?: NeuronNode("memoryHistory", NodeData("", NodeType.OPERATOR)).also {
            tree.addChild(root.id, it)
        }

    // Ensure all memory categories exist
    for (tag in MemoryDataTags.entries) {
        val nodeId = tag.toString().lowercase()
        if (tree.getNodeDirectOrNull(nodeId) == null) {
            createNewMemory(root, tag, JSONObject("""{"messages": []}"""))
        }
    }
}


fun readBrainFile(key: SecretKey, context: Context): NeuronTree {
    val brainFile = getBrainFilePath(context)
    return loadEncryptedTree(brainFile, key) ?: getDefaultBrainStructure()
}

fun getDefaultChatHistory(root: NeuronNode): NeuronNode {
    return NeuronTree(root).getNodeDirect("chatHistory")
}

fun getDefaultMemoryHistory(root: NeuronNode): NeuronNode {
    return NeuronTree(root).getNodeDirect("memoryHistory")
}

fun addNewChat(root: NeuronNode, data: JSONObject): NeuronNode {
    val chatHistory = getDefaultChatHistory(root)
    val newChat = NeuronNode(data = NodeData(data.toString(), NodeType.LEAF))
    NeuronTree(root).addChild(chatHistory.id, newChat)
    return newChat
}


fun saveTree(tree: NeuronTree, context: Context, alise: String) {
    val key = getOrCreateHardwareBackedAesKey(alise)
    val file = getBrainFilePath(context)
    saveEncryptedTree(tree, file, key)
}
