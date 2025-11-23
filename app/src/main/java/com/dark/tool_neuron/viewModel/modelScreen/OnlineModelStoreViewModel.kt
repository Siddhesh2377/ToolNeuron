package com.dark.tool_neuron.viewModel.modelScreen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.GGUFModels
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OnlineModelStoreViewModel : ViewModel() {

    companion object {
        private const val TAG = "OnlineModelStoreViewModel"
    }

    private val db = FirebaseFirestore.getInstance()

    private val _ggufModels = MutableStateFlow<List<GGUFModels>>(emptyList())
    val ggufModels: MutableStateFlow<List<GGUFModels>> = _ggufModels


    private fun observeGGUFModels() {
        viewModelScope.launch {
            db.collection("gguf-models").get().addOnSuccessListener {
                val models = it.toObjects(GGUFModels::class.java)
                _ggufModels.value = models
            }.addOnFailureListener {
                Log.e(TAG, "Error fetching GGUF models", it)
            }
        }
    }
}