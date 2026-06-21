package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
) {
    fun exportTo(uri: Uri, models: List<ModelInfo>) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                val manifestModels = JSONArray()
                models.forEach { model ->
                    val root = File(model.path)
                    if (!root.exists()) return@forEach
                    val safeId = safeName(model.id)
                    val rootEntry = if (root.isDirectory) {
                        "models/$safeId/${root.name}"
                    } else {
                        "models/$safeId/${root.name}"
                    }
                    val files = JSONArray()
                    if (root.isDirectory) {
                        root.walkTopDown().filter { it.isFile }.forEach { file ->
                            val rel = file.relativeTo(root).invariantSeparatorsPath
                            val entry = "$rootEntry/$rel"
                            putFile(zip, file, entry)
                            files.put(fileJson(entry, file))
                        }
                    } else {
                        putFile(zip, root, rootEntry)
                        files.put(fileJson(rootEntry, root))
                    }
                    manifestModels.put(
                        JSONObject()
                            .put("id", model.id)
                            .put("name", model.name)
                            .put("providerType", model.providerType.name)
                            .put("pathType", PathType.FILE.name)
                            .put("rootEntry", rootEntry)
                            .put("fileSize", model.fileSize)
                            .put("isDirectory", root.isDirectory)
                            .put("config", configJson(modelRepo.getConfig(model.id)))
                            .put("files", files),
                    )
                }
                val manifest = JSONObject()
                    .put("format", "tool-neuron-model-backup")
                    .put("version", 1)
                    .put("createdAt", System.currentTimeMillis())
                    .put("models", manifestModels)
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } ?: error("Could not open export destination")
    }

    fun importFrom(uri: Uri): Int {
        val root = File(context.filesDir, "imported_models/${System.currentTimeMillis()}").apply { mkdirs() }
        var manifestText: String? = null
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (name == "manifest.json") {
                        manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        continue
                    }
                    val outFile = safeOutputFile(root, name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                }
            }
        } ?: error("Could not open import source")
        val manifest = JSONObject(manifestText ?: error("Backup manifest missing"))
        check(manifest.optString("format") == "tool-neuron-model-backup") { "Not a ToolNeuron model backup" }
        val arr = manifest.getJSONArray("models")
        var imported = 0
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val id = item.getString("id")
            val restoredRoot = safeOutputFile(root, item.getString("rootEntry"))
            if (!restoredRoot.exists()) continue
            val provider = runCatching { ProviderType.valueOf(item.getString("providerType")) }
                .getOrDefault(ProviderType.GGUF)
            val folderSize = if (restoredRoot.isDirectory) {
                restoredRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                restoredRoot.length()
            }
            val config = item.optJSONObject("config")?.let {
                ModelConfig(
                    id = it.optString("id", id),
                    modelId = id,
                    loadingParamsJson = it.optString("loadingParamsJson", "{}"),
                    inferenceParamsJson = it.optString("inferenceParamsJson", "{}"),
                )
            }
            modelRepo.insert(
                ModelInfo(
                    id = id,
                    name = item.optString("name", id),
                    path = restoredRoot.absolutePath,
                    pathType = PathType.FILE,
                    providerType = provider,
                    fileSize = folderSize,
                    isActive = false,
                ),
                config,
            )
            imported++
        }
        return imported
    }

    private fun putFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun fileJson(entry: String, file: File): JSONObject =
        JSONObject()
            .put("entry", entry)
            .put("size", file.length())
            .put("sha256", sha256(file))

    private fun configJson(config: ModelConfig?): JSONObject? =
        config?.let {
            JSONObject()
                .put("id", it.id)
                .put("modelId", it.modelId)
                .put("loadingParamsJson", it.loadingParamsJson)
                .put("inferenceParamsJson", it.inferenceParamsJson)
        }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "model" }

    private fun safeOutputFile(root: File, entryName: String): File {
        val out = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        val outPath = out.canonicalPath
        check(outPath.startsWith(rootPath)) { "Unsafe backup entry: $entryName" }
        return out
    }
}
