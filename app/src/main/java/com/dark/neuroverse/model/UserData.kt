package com.dark.neuroverse.model

import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeType

enum class ViewMode {
    TREE, LIST, STATS
}

data class NodeStats(
    val total: Int,
    val byType: Map<NodeType, Int>,
    val totalContent: Int,
    val deepestLevel: Int
)