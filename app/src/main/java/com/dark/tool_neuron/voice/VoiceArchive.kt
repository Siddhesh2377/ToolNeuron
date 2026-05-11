package com.dark.tool_neuron.voice

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

object VoiceArchive {

    private const val EXTRACT_BUFFER_SIZE = 256 * 1024

    sealed class ExtractResult {
        data class Success(val folder: File, val configJson: String) : ExtractResult()
        data class Failure(val reason: String) : ExtractResult()
    }

    fun extractAndBuildConfig(
        archive: File,
        destParent: File,
        kind: VoiceKind,
        onEntry: (entryName: String) -> Unit = {},
    ): ExtractResult {
        val folderName = archive.name
            .removeSuffix(".tar.bz2")
            .removeSuffix(".tbz2")
            .removeSuffix(".tar")
        val dest = File(destParent, folderName)
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()

        try {
            val copyBuf = ByteArray(EXTRACT_BUFFER_SIZE)
            BufferedInputStream(FileInputStream(archive), EXTRACT_BUFFER_SIZE).use { fileIn ->
                BZip2CompressorInputStream(fileIn, true).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        while (true) {
                            val entry = tarIn.nextEntry ?: break
                            if (!tarIn.canReadEntryData(entry)) continue
                            val relative = entry.name.trimStart('/')
                            val target = safeResolve(dest, relative) ?: continue
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                onEntry(target.name)
                                target.outputStream().buffered(EXTRACT_BUFFER_SIZE).use { out ->
                                    while (true) {
                                        val n = tarIn.read(copyBuf)
                                        if (n < 0) break
                                        out.write(copyBuf, 0, n)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            dest.deleteRecursively()
            return ExtractResult.Failure(t.message ?: "Extraction failed")
        }

        val actualRoot = pickRoot(dest)
        val (files, subdirs) = collectTree(actualRoot)

        val config = when (kind) {
            VoiceKind.TTS -> buildTtsConfig(files, subdirs)
            VoiceKind.STT -> buildSttConfig(files)
        } ?: run {
            dest.deleteRecursively()
            return ExtractResult.Failure(
                when (kind) {
                    VoiceKind.TTS -> "Archive missing model.onnx + tokens.txt"
                    VoiceKind.STT -> "Archive missing encoder/decoder .onnx + tokens.txt"
                }
            )
        }
        return ExtractResult.Success(actualRoot, config.toString())
    }

    private fun pickRoot(dest: File): File {
        val entries = dest.listFiles() ?: return dest
        val dirs = entries.filter { it.isDirectory }
        val files = entries.filter { it.isFile }
        return if (files.isEmpty() && dirs.size == 1) dirs[0] else dest
    }

    private data class Tree(
        val files: Map<String, File>,
        val subdirs: Map<String, File>,
    )

    private fun collectTree(root: File): Pair<Map<String, File>, Map<String, File>> {
        val files = HashMap<String, File>()
        val subdirs = HashMap<String, File>()
        val entries = root.listFiles() ?: return files to subdirs
        for (entry in entries) {
            if (entry.isFile) files[entry.name.lowercase()] = entry
            else if (entry.isDirectory) subdirs[entry.name.lowercase()] = entry
        }
        return files to subdirs
    }

    private fun buildTtsConfig(
        files: Map<String, File>,
        subdirs: Map<String, File>,
    ): JSONObject? {
        val model = files.entries.firstOrNull { (n, _) ->
            n.endsWith(".onnx") && !n.contains("mmproj")
        }?.value ?: return null
        val tokens = files.entries.firstOrNull { (n, _) ->
            n.contains("tokens") && n.endsWith(".txt")
        }?.value ?: return null
        val dataDir = subdirs.entries.firstOrNull { (n, _) ->
            n.contains("espeak") || n == "data"
        }?.value
        return JSONObject().apply {
            put("model", model.absolutePath)
            put("tokens", tokens.absolutePath)
            if (dataDir != null) put("dataDir", dataDir.absolutePath)
            put("numThreads", 2)
        }
    }

    private fun buildSttConfig(files: Map<String, File>): JSONObject? {
        val encoder = files.entries.firstOrNull { (n, _) ->
            n.endsWith(".onnx") && n.contains("encoder")
        }?.value
        val decoder = files.entries.firstOrNull { (n, _) ->
            n.endsWith(".onnx") && n.contains("decoder")
        }?.value
        val tokens = files.entries.firstOrNull { (n, _) ->
            n.contains("tokens") && n.endsWith(".txt")
        }?.value
        if (encoder == null || decoder == null || tokens == null) return null
        return JSONObject().apply {
            put("type", "whisper")
            put("encoder", encoder.absolutePath)
            put("decoder", decoder.absolutePath)
            put("tokens", tokens.absolutePath)
            put("numThreads", 2)
        }
    }

    private fun safeResolve(root: File, relative: String): File? {
        val target = File(root, relative).canonicalFile
        val rootCanon = root.canonicalFile
        if (!target.toPath().startsWith(rootCanon.toPath())) return null
        return target
    }
}

enum class VoiceKind { TTS, STT }
