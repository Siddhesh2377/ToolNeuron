package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.URL

enum class UpdateStatus {
    IDLE, DOWNLOADING, READY_TO_INSTALL, FAILED
}

data class AppUpdateInfo(
    val hasUpdate: Boolean = false,
    val updateLink: String = "",
    val version: String = "",
    val whatsNew: List<String> = emptyList(),
    val downloadProgress: Int = 0,
    val apkFilePath: String = "",
    val status: UpdateStatus = UpdateStatus.IDLE
)

class UpdateViewModel : ViewModel() {
    private val _updateInfo = MutableStateFlow(AppUpdateInfo())
    val updateInfo: StateFlow<AppUpdateInfo> = _updateInfo


    fun downloadApkAndInstall(context: Context){
        val url = updateInfo.value.updateLink
        if (url.isBlank()) return

        viewModelScope.launch {
            try {
                _updateInfo.value = _updateInfo.value.copy(status = UpdateStatus.DOWNLOADING)
                val connection = URL(url).openConnection()
                val totalSize = connection.contentLength
                val input = connection.getInputStream()

                val file = File(context.cacheDir, "update_neurov.apk")
                val output = file.outputStream()

                val buffer = ByteArray(8192)
                var downloaded = 0
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val progress = (downloaded * 100 / totalSize)
                    _updateInfo.value = _updateInfo.value.copy(downloadProgress = progress)
                }

                output.flush()
                output.close()
                input.close()

                _updateInfo.value = _updateInfo.value.copy(
                    status = UpdateStatus.READY_TO_INSTALL,
                    apkFilePath = file.absolutePath
                )

            } catch (e: Exception) {
                Log.e("UpdateViewModel", "Download failed", e)
                _updateInfo.value = _updateInfo.value.copy(status = UpdateStatus.FAILED)
            }
        }
    }


    fun fetchUpdateInfo(jsonUrl: String) {
        viewModelScope.launch {
            try {
                val json = URL(jsonUrl).readText()
                val jsonObject = JSONObject(json)

                val info = AppUpdateInfo(
                    hasUpdate = jsonObject.optBoolean("hasUpdate", false),
                    updateLink = jsonObject.optString("updateLink", ""),
                    version = jsonObject.optString("version", ""),
                    whatsNew = jsonObject.optJSONArray("whatsNew")?.let { arr ->
                        List(arr.length()) { i -> arr.getString(i) }
                    } ?: emptyList(),
                    status = UpdateStatus.DOWNLOADING
                )

                _updateInfo.value = info

            } catch (e: Exception) {
                Log.e("UpdateViewModel", "Failed to fetch update info", e)
            }
        }
    }
}
