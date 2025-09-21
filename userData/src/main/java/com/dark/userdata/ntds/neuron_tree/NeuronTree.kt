package com.dark.userdata.ntds.neuron_tree

import android.util.Log
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
class NeuronTree(val root: NeuronNode) {

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
    fun getNodeDirect(id: String): NeuronNode = nodeMap[id] ?: NeuronNode()

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


    fun getAllChildrenRecursive(): List<NeuronNode> {
        val result = mutableListOf<NeuronNode>()
        fun collect(node: NeuronNode) {
            node.children.forEach {
                result.add(it)
                collect(it)
            }
        }
        collect(root)
        return result
    }

    fun deleteNodeById(id: String) {
        if (id == "root") {
            Log.d("NeuronTree", "Attempted to delete root, operation ignored.")
            return // Never delete root
        }

        val nodeToDelete = nodeMap[id]
        if (nodeToDelete == null) {
            Log.w("NeuronTree", "No node found with id=$id, nothing to delete.")
            return
        }

        val path = nodeIndex[id]
        if (path == null) {
            Log.w("NeuronTree", "No path found for node id=$id, inconsistent state.")
            return
        }

        Log.d("NeuronTree", "Deleting node id=$id at path=$path")

        // Step 1: Find parent path (e.g., from "root/0/2" → "root/0")
        val parentPath = path.substringBeforeLast("/", missingDelimiterValue = "")
        val parentEntry = nodeIndex.entries.find { it.value == parentPath }
        val parentNode = parentEntry?.key?.let { nodeMap[it] }

        if (parentNode == null) {
            Log.w("NeuronTree", "Parent not found for node id=$id, parentPath=$parentPath")
        } else {
            // Step 2: Remove from parent's children
            val removed = parentNode.children.removeIf { it.id == id }
            Log.d("NeuronTree", "Removed node id=$id from parent id=${parentNode.id}, success=$removed")
        }

        // Step 3: Remove this node and all descendants from maps
        fun removeRecursively(node: NeuronNode) {
            Log.d("NeuronTree", "Removing node id=${node.id}, childrenCount=${node.children.size}")
            nodeMap.remove(node.id)
            nodeIndex.remove(node.id)
            node.children.forEach { removeRecursively(it) }
        }

        removeRecursively(nodeToDelete)
        Log.d("NeuronTree", "Finished deleting node id=$id and its descendants")
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


