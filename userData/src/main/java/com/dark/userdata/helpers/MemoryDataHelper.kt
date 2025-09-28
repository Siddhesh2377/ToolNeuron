package com.dark.userdata.helpers

import com.dark.userdata.getDefaultMemoryHistory
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.ntds.neuron_tree.NodeData
import com.dark.userdata.ntds.neuron_tree.NodeType
import org.json.JSONObject

fun createNewMemory(root: NeuronNode, id: MemoryDataTags, data: JSONObject): NeuronNode {
    val chatHistory = getDefaultMemoryHistory(root)
    val newChat =
        NeuronNode(id = id.toString().lowercase(), data = NodeData(data.toString(), NodeType.STEAM))
    NeuronTree(root).addChild(chatHistory.id, newChat)
    return newChat
}

fun getMemoryByTags(root: NeuronNode, id: MemoryDataTags): NeuronNode? {
    return NeuronTree(root).getNodeDirect(id.toString().lowercase())
}

fun updateMemory(root: NeuronNode, id: MemoryDataTags, data: JSONObject) {
    NeuronTree(root).getNodeDirect(id.toString().lowercase()).data.content = data.toString()
}

enum class MemoryDataTags {
    Family, Friends, Work, Health, Education, Entertainment, Other
}

