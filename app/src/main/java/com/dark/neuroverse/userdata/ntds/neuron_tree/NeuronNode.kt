package com.dark.neuroverse.userdata.ntds.neuron_tree

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
open class NeuronNode(
    val id: String = UUID.randomUUID().toString(),
    val data: NodeData = NodeData("", NodeType.LEAF),
    val children: MutableList<NeuronNode> = mutableListOf()
) {

    fun getChildNodes(): List<NeuronNode> = children

    fun addChild(node: NeuronNode) {
        children.add(node)
    }

    open fun onDataRequested(request: String): NodeData {
        return data
    }
}


@Serializable
data class NodeData(
    var content: String, val type: NodeType
)

@Serializable
enum class NodeType {
    ROOT, OPERATOR, HOLDER, STEAM, LEAF
}