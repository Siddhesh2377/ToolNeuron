package com.dark.userdata.neuron_tree

import com.dark.userdata.schema.ChildNodeSchema
import com.dark.userdata.schema.NodeContentSchema
import com.dark.userdata.schema.NodeTags
import kotlinx.serialization.Serializable

/**
 * Manages the entire NeuronTree structure with fast lookup and path indexing.
 *
 * Example usage:
 * ```
 * val root = NeuronNode("root", "Root Data")
 * val tree = NeuronTree(root)
 *
 * val childA = NeuronNode("a", "Node A")
 * val childB = NeuronNode("b", "Node B")
 *
 * tree.addChild("root", childA, childB)
 *
 * println(tree.requestNodePath("a")) // Outputs: root/0
 * println(tree.getNodeDirect("b")?.getData()) // Outputs: Node B
 *
 * tree.printTree()
 * ```
 */
@Serializable
class NeuronTree(internal val root: NeuronNode) {

    /** Maps node ID to its unique path in the tree (e.g., root/0/1). */
    private val nodeIndex = mutableMapOf<String, String>()

    /** Maps node ID to the actual NeuronNode reference for fast access. */
    internal val nodeMap = mutableMapOf<String, NeuronNode>()

    init {
        indexNode(root, "root")
    }

    /**
     * Recursively indexes the provided node and its children into the lookup tables.
     *
     * @param node The node to index.
     * @param path The hierarchical path string for the node.
     */
    private fun indexNode(node: NeuronNode, path: String) {
        nodeIndex[node.id] = path
        nodeMap[node.id] = node
        node.children.forEachIndexed { index, child ->
            indexNode(child, "$path/$index")
        }
    }

    /**
     * Retrieves the tree path for a node by its ID.
     *
     * @param id The unique ID of the node.
     * @return The path string if the node exists, or null if not found.
     *
     * Example:
     * ```
     * val path = tree.requestNodePath("a")
     * println(path) // Outputs something like: root/0
     * ```
     */
    fun requestNodePath(id: String): String? = nodeIndex[id]

    /**
     * Retrieves the direct NeuronNode reference by ID.
     *
     * @param id The unique ID of the node.
     * @return The NeuronNode if found, or null if not found.
     *
     * Example:
     * ```
     * val node = tree.getNodeDirect("b")
     * println(node?.getData()) // Outputs: Node B
     * ```
     */
    fun getNodeDirect(id: String): NeuronNode? = nodeMap[id]

    /**
     * Adds one or more child nodes to the specified parent in the tree.
     * Automatically re-indexes the added nodes.
     *
     * @param parentId The ID of the parent node.
     * @param children The child nodes to add.
     *
     * Example:
     * ```
     * val child = NeuronNode("c", "Child Data")
     * tree.addChild("a", child)
     * ```
     */
    fun addChild(parentId: String, vararg children: NeuronNode) {
        val parent = nodeMap[parentId] ?: return
        children.forEach { child ->
            parent.children.add(child)
            indexNode(child, "${nodeIndex[parentId]}/${parent.children.size - 1}")
        }
    }

    /**
     * Prints the entire tree structure to the console with indentation for hierarchy.
     *
     * Example output:
     * ```
     * - [root] Root Data
     *   - [a] Node A
     *   - [b] Node B
     * ```
     */
    fun printTree() {
        val root = nodeMap["root"] ?: return
        printNodeRecursive(root, 0)
    }

    /**
     * Recursively prints nodes with indentation based on depth.
     *
     * @param node The current node to print.
     * @param depth The depth level, used for indentation.
     */
    private fun printNodeRecursive(node: NeuronNode, depth: Int) {
        println("${" ".repeat(depth * 2)}- [${node.id}] ${node.data}")
        node.children.forEach { child ->
            printNodeRecursive(child, depth + 1)
        }
    }


    /**
     * Recursively prints nodes ID's.
     */
    fun printAllIds() {
        println("Registered Node IDs:")
        nodeMap.keys.forEach { println(it) }
    }

}

fun getDefaultBrainStructure(): NeuronTree {
    val tools = generateOperatorNode("tools", OperatorNodesContent.TOOLS)
    val temp = generateOperatorNode("temp-mem", OperatorNodesContent.TEMPORARY_MEMORY)
    val main = generateOperatorNode("main-mem", OperatorNodesContent.MAIN_MEMORY)
    val ai = generateOperatorNode("ai", OperatorNodesContent.AI)

    val root = NeuronNode("root", NodeData(NodeContentSchema(
        name = "root",
        tags = listOf(NodeTags.ROOT, NodeTags.PR_VERY_HIGH),
        childNodes = listOf(
            ChildNodeSchema(tools.id),
            ChildNodeSchema(temp.id),
            ChildNodeSchema(main.id),
            ChildNodeSchema(ai.id)
        )
    ), NodeType.ROOT))
    val tree = NeuronTree(root)

    tree.addChild(root.id, tools, temp, main, ai)

    return tree
}

internal fun generateOperatorNode(id: String, content: NodeContentSchema = NodeContentSchema()): NeuronNode{
    return NeuronNode(id, NodeData(content, NodeType.OPERATOR))
}

internal object OperatorNodesContent{
    val TOOLS = NodeContentSchema(
        name = "tool",
        tags = listOf(NodeTags.TOOL, NodeTags.APP_OPERATOR, NodeTags.PR_VERY_HIGH),
        childNodes = mutableListOf(ChildNodeSchema())
    )
    val TEMPORARY_MEMORY = NodeContentSchema(
        name = "temporary-memory",
        tags = listOf(NodeTags.TEMP, NodeTags.PR_LOW, NodeTags.TEMP_MESSAGES),
        childNodes = mutableListOf(ChildNodeSchema())
    )
    val MAIN_MEMORY = NodeContentSchema(
        name = "main-memory",
        tags = listOf(NodeTags.MAIN, NodeTags.PR_MEDIUM, NodeTags.MAIN_CONVERSATION),
        childNodes = mutableListOf(ChildNodeSchema())
    )
    val AI = NodeContentSchema(
        name = "ai",
        tags = listOf(NodeTags.AI, NodeTags.PR_HIGH),
        childNodes = mutableListOf(ChildNodeSchema())
    )
}
