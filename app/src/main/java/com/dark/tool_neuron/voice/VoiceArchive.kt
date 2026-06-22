package com.dark.tool_neuron.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object VoiceArchive {

    private const val READ_BUFFER_SIZE = 256 * 1024
    private const val PIPELINE_CAPACITY = 16

    sealed class ExtractResult {
        data class Success(val folder: File, val configJson: String) : ExtractResult()
        data class Failure(val reason: String) : ExtractResult()
    }

    private sealed class WriteOp {
        data class OpenFile(val target: File) : WriteOp()
        data class Chunk(val bytes: ByteArray, val length: Int) : WriteOp()
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
        val finalDest = File(destParent, folderName)
        val tempDest = File(destParent, "$folderName.installing-${System.currentTimeMillis()}")
        tempDest.deleteRecursively()
        tempDest.mkdirs()

        try {
            runBlocking {
                val channel = Channel<WriteOp>(PIPELINE_CAPACITY)

                val writer = launch(Dispatchers.IO) {
                    var stream: BufferedOutputStream? = null
                    try {
                        for (op in channel) {
                            when (op) {
                                is WriteOp.OpenFile -> {
                                    stream?.close()
                                    stream = BufferedOutputStream(
                                        FileOutputStream(op.target),
                                        READ_BUFFER_SIZE,
                                    )
                                }
                                is WriteOp.Chunk -> {
                                    stream?.write(op.bytes, 0, op.length)
                                }
                            }
                        }
                    } finally {
                        stream?.close()
                    }
                }

                val producer = launch(Dispatchers.Default) {
                    try {
                        BufferedInputStream(FileInputStream(archive), READ_BUFFER_SIZE).use { fileIn ->
                            BZip2CompressorInputStream(fileIn, true).use { bzIn ->
                                TarArchiveInputStream(bzIn).use { tarIn ->
                                    while (true) {
                                        val entry = tarIn.nextEntry ?: break
                                        if (!tarIn.canReadEntryData(entry)) continue
                                        val relative = entry.name.trimStart('/')
                                        val target = safeResolve(tempDest, relative) ?: continue
                                        if (entry.isDirectory) {
                                            target.mkdirs()
                                        } else {
                                            target.parentFile?.mkdirs()
                                            onEntry(target.name)
                                            channel.send(WriteOp.OpenFile(target))
                                            while (true) {
                                                val buf = ByteArray(READ_BUFFER_SIZE)
                                                val n = tarIn.read(buf)
                                                if (n < 0) break
                                                channel.send(WriteOp.Chunk(buf, n))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        channel.close()
                    }
                }

                producer.join()
                writer.join()
            }
        } catch (t: Throwable) {
            tempDest.deleteRecursively()
            return ExtractResult.Failure(t.message ?: "Extraction failed")
        }

        val extractedRoot = pickRoot(tempDest)
        val (files, subdirs) = collectTree(extractedRoot)

        val valid = when (kind) {
            VoiceKind.TTS -> buildTtsConfig(files, subdirs)
            VoiceKind.STT -> buildSttConfig(files)
        } ?: run {
            tempDest.deleteRecursively()
            return ExtractResult.Failure(
                when (kind) {
                    VoiceKind.TTS -> "Archive missing model.onnx + tokens.txt"
                    VoiceKind.STT -> "Archive missing encoder/decoder .onnx + tokens.txt"
                }
            )
        }
        if (valid.length() <= 0) {
            tempDest.deleteRecursively()
            return ExtractResult.Failure("Archive validation failed")
        }

        try {
            finalDest.deleteRecursively()
            finalDest.parentFile?.mkdirs()
            val moved = extractedRoot.renameTo(finalDest)
            if (!moved) {
                extractedRoot.copyRecursively(finalDest, overwrite = true)
            }
            tempDest.deleteRecursively()
        } catch (t: Throwable) {
            finalDest.deleteRecursively()
            tempDest.deleteRecursively()
            return ExtractResult.Failure(t.message ?: "Failed to finalize install")
        }

        val (finalFiles, finalSubdirs) = collectTree(finalDest)
        val config = when (kind) {
            VoiceKind.TTS -> buildTtsConfig(finalFiles, finalSubdirs)
            VoiceKind.STT -> buildSttConfig(finalFiles)
        } ?: return ExtractResult.Failure("Installed voice model could not be verified")
        return ExtractResult.Success(finalDest, config.toString())
    }

    private fun pickRoot(dest: File): File {
        val entries = dest.listFiles() ?: return dest
        val dirs = entries.filter { it.isDirectory }
        val files = entries.filter { it.isFile }
        return if (files.isEmpty() && dirs.size == 1) dirs[0] else dest
    }

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
