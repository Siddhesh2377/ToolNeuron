package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import com.dark.tool_neuron.data.AppPreferences
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

data class BackupProgress(
    val label: String,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val startedAtMs: Long = System.currentTimeMillis(),
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    val etaSeconds: Long?
        get() {
            if (processedBytes <= 0L || totalBytes <= 0L) return null
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
            val bytesPerMs = processedBytes.toDouble() / elapsedMs.toDouble()
            if (bytesPerMs <= 0.0) return null
            return (((totalBytes - processedBytes).coerceAtLeast(0L)) / bytesPerMs / 1000.0).toLong()
        }
}

enum class BackupConflict {
    NEW,
    SAME_ID_EXISTS,
}

data class BackupModelPreview(
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val fileCount: Int,
    val totalBytes: Long,
    val conflict: BackupConflict,
)

data class BackupPreview(
    val version: Int,
    val createdAt: Long,
    val models: List<BackupModelPreview>,
    val hasSettings: Boolean = false,
)

@Singleton
class ModelBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val prefs: AppPreferences,
) {
    fun exportTo(uri: Uri, models: List<ModelInfo>, onProgress: (BackupProgress) -> Unit = {}) {
        val startedAt = System.currentTimeMillis()
        val exportModels = models.mapNotNull(::buildExportRoot)
        val totalBytes = exportModels.sumOf { root -> root.files.sumOf { it.size.coerceAtLeast(0L) } }.coerceAtLeast(0L)
        var processed = 0L
        onProgress(BackupProgress("Preparing export", 0L, totalBytes, startedAt))
        context.contentResolver.openOutputStream(uri, "wt")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                val manifestModels = JSONArray()
                exportModels.forEach { exportRoot ->
                    val model = exportRoot.model
                    val files = JSONArray()
                    exportRoot.files.forEach { source ->
                        val record = putExportFile(zip, source) { copied ->
                            processed += copied
                            onProgress(BackupProgress("Exporting ${model.name}", processed, totalBytes, startedAt))
                        }
                        files.put(
                            JSONObject()
                                .put("entry", source.entry)
                                .put("size", record.size)
                                .put("sha256", record.sha256),
                        )
                    }
                    manifestModels.put(
                        JSONObject()
                            .put("id", model.id)
                            .put("name", model.name)
                            .put("providerType", model.providerType.name)
                            .put("pathType", PathType.FILE.name)
                            .put("rootEntry", exportRoot.rootEntry)
                            .put("fileSize", model.fileSize)
                            .put("isDirectory", exportRoot.isDirectory)
                            .put("config", configJson(modelRepo.getConfig(model.id)))
                            .put("files", files),
                    )
                }
                val manifest = JSONObject()
                    .put("format", "tool-neuron-model-backup")
                    .put("version", 1)
                    .put("createdAt", System.currentTimeMillis())
                    .put("settings", buildSettingsJson())
                    .put("models", manifestModels)
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            onProgress(BackupProgress("Export complete", totalBytes, totalBytes, startedAt))
        } ?: error("Could not open export destination")
    }

    private data class ExportRoot(
        val model: ModelInfo,
        val rootEntry: String,
        val isDirectory: Boolean,
        val files: List<ExportFile>,
    )

    private data class ExportFile(
        val entry: String,
        val file: File? = null,
        val uri: Uri? = null,
        val size: Long = 0L,
    )

    private data class ExportRecord(
        val size: Long,
        val sha256: String,
    )

    private fun buildExportRoot(model: ModelInfo): ExportRoot? {
        val safeId = safeName(model.id)
        return when (model.pathType) {
            PathType.FILE -> {
                val root = File(model.path)
                if (!root.exists()) return null
                val rootEntry = "models/$safeId/${safeName(root.name)}"
                val files = if (root.isDirectory) {
                    root.walkTopDown()
                        .filter { it.isFile }
                        .map { file ->
                            val rel = file.relativeTo(root).invariantSeparatorsPath
                            ExportFile(entry = "$rootEntry/$rel", file = file, size = file.length())
                        }
                        .toList()
                } else {
                    listOf(ExportFile(entry = rootEntry, file = root, size = root.length()))
                }
                ExportRoot(model = model, rootEntry = rootEntry, isDirectory = root.isDirectory, files = files)
            }
            PathType.CONTENT_URI -> {
                val contentUri = Uri.parse(model.path)
                val fileName = safeContentFileName(contentUri, model)
                val rootEntry = "models/$safeId/$fileName"
                ExportRoot(
                    model = model,
                    rootEntry = rootEntry,
                    isDirectory = false,
                    files = listOf(ExportFile(entry = rootEntry, uri = contentUri, size = model.fileSize)),
                )
            }
        }
    }

    fun preview(uri: Uri): BackupPreview {
        val manifest = readManifest(uri)
        check(manifest.optString("format") == "tool-neuron-model-backup") { "Not a ToolNeuron model backup" }
        val existingIds = modelRepo.models.value.map { it.id }.toSet()
        val arr = manifest.getJSONArray("models")
        val models = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.getString("id")
                val files = item.optJSONArray("files") ?: JSONArray()
                val totalBytes = (0 until files.length()).sumOf { idx ->
                    files.getJSONObject(idx).optLong("size", 0L)
                }
                add(
                    BackupModelPreview(
                        id = id,
                        name = item.optString("name", id),
                        providerType = runCatching { ProviderType.valueOf(item.getString("providerType")) }
                            .getOrDefault(ProviderType.GGUF),
                        fileCount = files.length(),
                        totalBytes = totalBytes,
                        conflict = if (id in existingIds) BackupConflict.SAME_ID_EXISTS else BackupConflict.NEW,
                    )
                )
            }
        }
        return BackupPreview(
            version = manifest.optInt("version", 1),
            createdAt = manifest.optLong("createdAt", 0L),
            models = models,
            hasSettings = manifest.optJSONObject("settings") != null,
        )
    }

    fun importFrom(
        uri: Uri,
        selectedIds: Set<String>? = null,
        overwriteExisting: Boolean = true,
        restoreSettings: Boolean = true,
        onProgress: (BackupProgress) -> Unit = {},
    ): Int {
        val startedAt = System.currentTimeMillis()
        val root = File(context.filesDir, "imported_models/${System.currentTimeMillis()}").apply { mkdirs() }
        var manifestText: String? = null
        var totalBytes = 0L
        var processed = 0L
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (entry.size > 0) totalBytes += entry.size
                    if (name == "manifest.json") {
                        manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        continue
                    }
                    val outFile = safeOutputFile(root, name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        val copied = zip.copyCountingTo(out) { copied ->
                            processed += copied
                            onProgress(BackupProgress("Importing ${name.substringAfterLast('/')}", processed, totalBytes, startedAt))
                        }
                        if (entry.size <= 0) totalBytes += copied
                    }
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
            if (selectedIds != null && id !in selectedIds) continue
            if (!overwriteExisting && modelRepo.getModelById(id) != null) continue
            val restoredRoot = safeOutputFile(root, item.getString("rootEntry"))
            if (!restoredRoot.exists()) continue
            verifyFiles(root, item.optJSONArray("files") ?: JSONArray())
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
        if (restoreSettings) {
            restoreSettingsJson(manifest.optJSONObject("settings"))
        }
        onProgress(BackupProgress("Import complete", totalBytes, totalBytes, startedAt))
        return imported
    }

    private fun buildSettingsJson(): JSONObject = JSONObject()
        .put("serverSelectedModelId", prefs.serverSelectedModelId)
        .put("serverModelRolesJson", prefs.serverModelRolesJson)
        .put("serverRoleDefaultsJson", prefs.serverRoleDefaultsJson)
        .put("activeTtsModelId", prefs.activeTtsModelId)
        .put("activeSttModelId", prefs.activeSttModelId)

    private fun restoreSettingsJson(settings: JSONObject?) {
        if (settings == null) return
        prefs.serverSelectedModelId = settings.optString("serverSelectedModelId", prefs.serverSelectedModelId)
        prefs.serverModelRolesJson = settings.optString("serverModelRolesJson", prefs.serverModelRolesJson)
        prefs.serverRoleDefaultsJson = settings.optString("serverRoleDefaultsJson", prefs.serverRoleDefaultsJson)
        prefs.activeTtsModelId = settings.optString("activeTtsModelId", prefs.activeTtsModelId)
        prefs.activeSttModelId = settings.optString("activeSttModelId", prefs.activeSttModelId)
    }

    private fun readManifest(uri: Uri): JSONObject {
        var manifestText: String? = null
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == "manifest.json") {
                        manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        break
                    }
                }
            }
        } ?: error("Could not open backup source")
        return JSONObject(manifestText ?: error("Backup manifest missing"))
    }

    private fun putFile(zip: ZipOutputStream, file: File, entryName: String, onBytes: (Long) -> Unit = {}) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyCountingTo(zip, onBytes) }
        zip.closeEntry()
    }

    private fun putExportFile(zip: ZipOutputStream, source: ExportFile, onBytes: (Long) -> Unit = {}): ExportRecord {
        zip.putNextEntry(ZipEntry(source.entry))
        val md = MessageDigest.getInstance("SHA-256")
        val copied = when {
            source.file != null -> FileInputStream(source.file).use { it.copyDigestingTo(zip, md, onBytes) }
            source.uri != null -> {
                val input = context.contentResolver.openInputStream(source.uri)
                    ?: error("Could not open model file: ${source.uri}")
                input.use { it.copyDigestingTo(zip, md, onBytes) }
            }
            else -> error("Backup source missing")
        }
        zip.closeEntry()
        return ExportRecord(
            size = copied,
            sha256 = md.digest().joinToString("") { "%02x".format(it) },
        )
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

    private fun verifyFiles(root: File, files: JSONArray) {
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            val entry = item.optString("entry")
            val expectedSize = item.optLong("size", -1L)
            val expectedHash = item.optString("sha256")
            val file = safeOutputFile(root, entry)
            check(file.exists()) { "Backup file missing: $entry" }
            if (expectedSize >= 0L) check(file.length() == expectedSize) { "Backup size mismatch: $entry" }
            if (expectedHash.isNotBlank()) check(sha256(file).equals(expectedHash, ignoreCase = true)) {
                "Backup checksum mismatch: $entry"
            }
        }
    }

    private fun java.io.InputStream.copyCountingTo(
        out: java.io.OutputStream,
        onBytes: (Long) -> Unit,
    ): Long {
        var bytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
            bytes += read
            onBytes(read.toLong())
        }
        return bytes
    }

    private fun java.io.InputStream.copyDigestingTo(
        out: java.io.OutputStream,
        digest: MessageDigest,
        onBytes: (Long) -> Unit,
    ): Long {
        var bytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            out.write(buffer, 0, read)
            bytes += read
            onBytes(read.toLong())
        }
        return bytes
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "model" }

    private fun safeContentFileName(uri: Uri, model: ModelInfo): String {
        val candidate = Uri.decode(uri.lastPathSegment.orEmpty())
            .substringAfterLast('/')
            .substringAfterLast(':')
            .ifBlank { model.name }
        val safe = safeName(candidate)
        return if (safe.contains('.')) safe else safeName(model.name).ifBlank { safeName(model.id) }
    }

    private fun safeOutputFile(root: File, entryName: String): File {
        val out = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        val outPath = out.canonicalPath
        check(outPath.startsWith(rootPath)) { "Unsafe backup entry: $entryName" }
        return out
    }
}
