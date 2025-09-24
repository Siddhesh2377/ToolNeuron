package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.downloadFile
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.DataPack
import com.google.firebase.firestore.FirebaseFirestore
import com.mp.data_hub_lib.manager.DataHubManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DataPackUiState(
    val dataPack: DataPack,
    val progress: Float = 0f, // 0.0 → 1.0
    val isDownloading: Boolean = false,
    val isInstalled: Boolean = false
)

class DataPackScreenViewModel : ViewModel() {

    private val _packs = MutableStateFlow<List<DataPackUiState>>(emptyList())
    val packs = _packs.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    fun loadDataPacks() {
        Log.d("DataPackScreenViewModel", "${DataHubManager.installedDataSets.value}")

        viewModelScope.launch {
            db.collection("data-packs")
                .get()
                .addOnSuccessListener { result ->
                    val packs = result.documents.mapNotNull { doc ->
                        doc.toObject(DataPack::class.java)
                    }.map { pack ->
                        DataPackUiState(
                            dataPack = pack,
                            isInstalled = isDownloaded(pack)
                        )
                    }
                    _packs.value = packs
                }
                .addOnFailureListener { e ->
                    Log.w("FirestoreDB", "Error fetching data packs", e)
                }
        }
    }

    private fun isDownloaded(dataPack: DataPack): Boolean {
        return DataHubManager.installedDataSets.value.find {
            it.modelName == dataPack.name
        } != null
    }

    fun installDataPack(dataPack: DataPack, context: Context) {
        updateUiState(dataPack) { it.copy(isDownloading = true, progress = 0f) }

        val file = File(context.filesDir, "data-packs/${dataPack.name}.vecx")
        file.parentFile?.mkdirs()

        viewModelScope.launch {
            downloadFile(
                fileUrl = dataPack.link,
                outputFile = file,
                onProgress = { progress ->
                    updateUiState(dataPack) { it.copy(progress = progress) }
                },
                onComplete = {
                    DataHubManager.installPack(file, password = BuildConfig.ALIAS) { success ->
                        if (success) {
                            updateUiState(dataPack) { it.copy(isDownloading = false, isInstalled = true, progress = 1f) }
                        } else {
                            updateUiState(dataPack) { it.copy(isDownloading = false, isInstalled = false, progress = 0f) }
                        }
                    }
                },
                onError = { e ->
                    Log.e("DataPackDL", "Download failed", e)
                    updateUiState(dataPack) { it.copy(isDownloading = false, progress = 0f) }
                }
            )
        }
    }

    fun deleteDataPack(dataPack: DataPack, context: Context) {
        val file = File(context.filesDir, "data-packs/${dataPack.name}.vecx")
        if (file.exists()) file.delete()

        DataHubManager.uninstallPack(dataPack.name)

        updateUiState(dataPack) { it.copy(isInstalled = false, progress = 0f, isDownloading = false) }
    }

    private fun updateUiState(dataPack: DataPack, update: (DataPackUiState) -> DataPackUiState) {
        _packs.value = _packs.value.map {
            if (it.dataPack.name == dataPack.name) update(it) else it
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}
