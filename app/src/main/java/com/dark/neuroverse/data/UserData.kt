package com.dark.neuroverse.data

import android.content.Context
import com.dark.userdata.getBrainFilePath
import com.dark.userdata.getOrCreateHardwareBackedAesKey
import com.dark.userdata.loadEncryptedTree
import com.dark.userdata.neuron_tree.NeuronNode
import com.dark.userdata.neuron_tree.NeuronTree
import com.dark.userdata.neuron_tree.NodeData
import com.dark.userdata.neuron_tree.NodeType
import com.dark.userdata.saveEncryptedTree
import com.dark.userdata.schema.NodeContentSchema

object UserData {
    var rootTree: NeuronTree? = NeuronTree(NeuronNode("root", NodeData(NodeContentSchema(), NodeType.ROOT)))

    fun init(context: Context){
        rootTree = loadEncryptedTree(getBrainFilePath(context), getOrCreateHardwareBackedAesKey())
    }

    fun updateBrain(context: Context){
        saveEncryptedTree(rootTree!!, getBrainFilePath(context), getOrCreateHardwareBackedAesKey())
    }
}