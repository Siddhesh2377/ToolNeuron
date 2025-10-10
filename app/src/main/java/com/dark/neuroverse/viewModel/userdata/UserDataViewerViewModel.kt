package com.dark.neuroverse.viewModel.userdata

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.NodeStats
import com.dark.neuroverse.model.ViewMode
import com.dark.neuroverse.userdata.ntds.collectAllNodes
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronNode
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeType
import com.dark.neuroverse.userdata.readBrainFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.crypto.SecretKey

@SuppressLint("StaticFieldLeak")
class UserDataViewerViewModel( private val context: Context) : ViewModel() {

    private val _tree = MutableStateFlow<NeuronTree?>(null)
    val tree: StateFlow<NeuronTree?> = _tree.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<String?>("root")
    val selectedNodeId: StateFlow<String?> = _selectedNodeId.asStateFlow()

    private val _expandedNodes = MutableStateFlow<Set<String>>(setOf("root"))
    val expandedNodes: StateFlow<Set<String>> = _expandedNodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.TREE)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val encryptionKey: SecretKey = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)

    init {
        loadTree()
    }

    fun loadTree() {
        _isLoading.value = true
        try {
            val loadedTree = readBrainFile(encryptionKey, context)
            _tree.value = loadedTree
        } catch (e: Exception) {
            // Handle error silently for clean UX
        } finally {
            _isLoading.value = false
        }
    }

    fun selectNode(nodeId: String) {
        _selectedNodeId.value = nodeId
    }

    fun toggleNodeExpansion(nodeId: String) {
        _expandedNodes.update { expanded ->
            if (nodeId in expanded) {
                expanded - nodeId
            } else {
                expanded + nodeId
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun getSelectedNode(): NeuronNode? {
        val nodeId = _selectedNodeId.value ?: return null
        return _tree.value?.getNodeDirect(nodeId)?.takeIf { it.id.isNotEmpty() }
    }

    fun getFilteredNodes(): List<NeuronNode> {
        val query = _searchQuery.value.lowercase()
        return _tree.value?.collectAllNodes()?.filter { node ->
            query.isEmpty() || node.id.lowercase().contains(query) || node.data.content.lowercase()
                .contains(query) || node.data.type.name.lowercase().contains(query)
        } ?: emptyList()
    }

    fun getNodeStats(): NodeStats {
        val allNodes = _tree.value?.collectAllNodes() ?: emptyList()
        return NodeStats(
            total = allNodes.size,
            byType = NodeType.entries.associateWith { type ->
                allNodes.count { it.data.type == type }
            },
            totalContent = allNodes.sumOf { it.data.content.length },
            deepestLevel = calculateMaxDepth(_tree.value?.root)
        )
    }

    private fun calculateMaxDepth(node: NeuronNode?, currentDepth: Int = 0): Int {
        if (node == null) return currentDepth
        return if (node.children.isEmpty()) {
            currentDepth
        } else {
            node.children.maxOfOrNull { calculateMaxDepth(it, currentDepth + 1) } ?: currentDepth
        }
    }
}

class UserDataViewerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UserDataViewerViewModel(context) as T
    }
}