package com.dark.neuroverse.viewModel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.neuroverse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.URL

// --- UpdateStatus.kt ---
enum class UpdateStatus {
    IDLE, DOWNLOADING, READY_TO_INSTALL, FAILED
}

// --- AppUpdateInfo.kt ---
data class AppUpdateInfo(
    val hasUpdate: Boolean = false,
    val updateLink: String = "",
    val version: String = "",
    val whatsNew: List<String> = emptyList(),
    val downloadProgress: Int = 0,
    val apkFilePath: String = "",
    val status: UpdateStatus = UpdateStatus.IDLE
)

// --- UpdateViewModel.kt ---
class UpdateViewModel : ViewModel() {
    private val _updateInfo = MutableStateFlow(AppUpdateInfo())
    val updateInfo: StateFlow<AppUpdateInfo> = _updateInfo

    fun fetchUpdateInfo(jsonUrl: String) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    URL(jsonUrl).readText()
                }

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


    fun downloadApk(context: Context) {
        val url = updateInfo.value.updateLink
        if (url.isBlank()) return

        viewModelScope.launch {
            try {
                _updateInfo.value = _updateInfo.value.copy(status = UpdateStatus.DOWNLOADING)
                val connection = URL(url).openConnection()
                val totalSize = connection.contentLength
                val input = BufferedInputStream(connection.getInputStream())

                val file = File(context.cacheDir, "update_${System.currentTimeMillis()}.apk")
                val output = BufferedOutputStream(file.outputStream())

                val buffer = ByteArray(8192)
                var downloaded = 0
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val progress = (downloaded * 100 / totalSize).coerceIn(0, 100)
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

    fun triggerInstall(context: Context) {
        val apkPath = updateInfo.value.apkFilePath
        if (apkPath.isBlank()) return

        val apkFile = File(apkPath)
        val apkUri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.provider", apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
