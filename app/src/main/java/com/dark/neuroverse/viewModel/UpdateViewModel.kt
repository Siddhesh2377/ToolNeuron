package com.dark.neuroverse.viewModel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
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
import androidx.core.net.toUri
import com.mp.updatemanager.UpdateChecker

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
    val downloadProgress: Float = 0f,
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
                    status = UpdateStatus.IDLE

                )

                _updateInfo.value = info

            } catch (e: Exception) {
                Log.e("UpdateViewModel", "Failed to fetch update info", e)
            }
        }
    }


    fun downloadApk(context: Context) {
        val url = updateInfo.value.updateLink
        Log.d("UpdateViewModel", "Downloading APK from: $url")
        if (url.isBlank()) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    _updateInfo.value = _updateInfo.value.copy(status = UpdateStatus.DOWNLOADING)
                    val connection = URL(url).openConnection()
                    val totalSize = connection.contentLengthLong
                    val isKnownSize = totalSize > 0

                    val input = BufferedInputStream(connection.getInputStream())

                    val file = File(context.cacheDir, "update_${System.currentTimeMillis()}.apk")
                    val output = BufferedOutputStream(file.outputStream())

                    val buffer = ByteArray(8192)
                    var downloaded = 0f
                    var read: Int

                    var lastProgress = -1f

                    if (isKnownSize) {
                        val progress = (downloaded.toDouble() / totalSize).coerceIn(0.0, 1.0).toFloat()
                        if (progress != lastProgress) {
                            _updateInfo.value = _updateInfo.value.copy(downloadProgress = progress.toFloat())
                            lastProgress = progress
                        }
                    }

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read

                        val progress = (downloaded.toDouble() / totalSize).coerceIn(0.0, 1.0).toFloat()

                        if (progress != lastProgress) {
                            _updateInfo.value = _updateInfo.value.copy(downloadProgress = progress)
                            lastProgress = progress
                        }
                    }



                    output.flush()
                    output.close()
                    input.close()

                    _updateInfo.value = _updateInfo.value.copy(
                        status = UpdateStatus.READY_TO_INSTALL,
                        apkFilePath = file.absolutePath
                    )
                }
            } catch (e: Exception) {
                Log.e("UpdateViewModel", "Download failed", e)
                _updateInfo.value = _updateInfo.value.copy(status = UpdateStatus.FAILED)
            }
        }
    }

    fun triggerInstall(context: Context) {

        val canInstall =
            context.packageManager.canRequestPackageInstalls()

        if (!canInstall) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }


        val apkPath = updateInfo.value.apkFilePath
        if (apkPath.isBlank()) {
            Log.e("UpdateViewModel", "APK path is blank.")
            return
        }

        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Log.e("UpdateViewModel", "APK file does not exist at path: $apkPath")
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Check if any app can handle this intent
        val pm = context.packageManager
        if (intent.resolveActivity(pm) != null) {
            Log.d("UpdateViewModel", "Launching install for URI: $apkUri")
            context.startActivity(intent)
        } else {
            Log.e("UpdateViewModel", "No app can handle the install intent.")
            Toast.makeText(context, "Installer not available on device", Toast.LENGTH_LONG).show()
        }
    }

    fun checkForUpdateAndStartDownload(context: Context, jsonUrl: String) {
        viewModelScope.launch {
            val result = UpdateChecker.checkForUpdate(context, jsonUrl)

            if (result != null && result.hasUpdate) {
                _updateInfo.value = _updateInfo.value.copy(
                    hasUpdate = true,
                    version = result.version,
                    whatsNew = result.whatsNew,
                    status = UpdateStatus.IDLE
                )

                // Wait briefly for UI state to update (optional)
                kotlinx.coroutines.delay(1000)

                downloadApk(context)
            }
        }
    }

}
