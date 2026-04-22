package com.dark.tool_neuron.terminal

import android.content.Context
import android.os.Build
import android.system.Os
import com.termux.shared.termux.TermuxConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BootstrapState {
    data object Idle : BootstrapState
    data class Running(val step: String, val progress: Float) : BootstrapState
    data object Done : BootstrapState
    data class Failed(val message: String, val cause: Throwable? = null) : BootstrapState
}

@Singleton
class BootstrapInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val prefixDir: File = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
    val homeDir: File = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
    val stagingDir: File = File(prefixDir.parentFile, "usr-staging")

    private val _state = MutableStateFlow<BootstrapState>(BootstrapState.Idle)
    val state: StateFlow<BootstrapState> = _state.asStateFlow()

    fun isInstalled(): Boolean {
        val bash = File(prefixDir, "bin/bash")
        val apt = File(prefixDir, "bin/apt")
        return bash.canExecute() && apt.canExecute()
    }

    suspend fun install(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = BootstrapState.Running("preparing", 0.01f)
            ensureBaseDirs()

            _state.value = BootstrapState.Running("wiping previous install", 0.05f)
            prefixDir.deleteRecursively()
            stagingDir.deleteRecursively()
            if (!stagingDir.mkdirs()) error("could not create staging dir at ${stagingDir.path}")

            val assetName = pickAsset()
            _state.value = BootstrapState.Running("extracting $assetName", 0.10f)
            val symlinksRaw = extractZipFromAssets(assetName, stagingDir) { frac ->
                _state.value = BootstrapState.Running("extracting $assetName", 0.10f + 0.55f * frac)
            }

            _state.value = BootstrapState.Running("rebranding prefix", 0.65f)
            patchPrefixInTextFiles(stagingDir)

            _state.value = BootstrapState.Running("creating symlinks", 0.75f)
            applySymlinks(symlinksRaw, stagingDir)

            _state.value = BootstrapState.Running("setting permissions", 0.82f)
            applyExecutablePermissions(stagingDir)

            _state.value = BootstrapState.Running("finalising install", 0.90f)
            if (!stagingDir.renameTo(prefixDir)) {
                error("failed to move staging into ${prefixDir.path}")
            }

            homeDir.mkdirs()

            _state.value = BootstrapState.Done
            Result.success(Unit)
        } catch (t: Throwable) {
            _state.value = BootstrapState.Failed(t.message ?: t.javaClass.simpleName, t)
            stagingDir.deleteRecursively()
            Result.failure(t)
        }
    }

    fun uninstall(): Boolean {
        val ok1 = prefixDir.deleteRecursively()
        val ok2 = stagingDir.deleteRecursively()
        val ok3 = homeDir.deleteRecursively()
        _state.value = BootstrapState.Idle
        return ok1 && ok2 && ok3
    }

    private fun ensureBaseDirs() {
        val base = prefixDir.parentFile ?: error("no parent for prefix dir")
        if (!base.exists() && !base.mkdirs()) error("could not create ${base.path}")
    }

    private fun pickAsset(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when (abi) {
            "arm64-v8a" -> "bootstrap/bootstrap-aarch64.zip"
            "x86_64" -> "bootstrap/bootstrap-x86_64.zip"
            else -> error("unsupported abi: $abi")
        }
    }

    private fun extractZipFromAssets(
        assetName: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ): String {
        var symlinksContent = ""
        var entryCount = 0
        val expectedEntries = 12_000

        context.assets.open(assetName).use { rawIn ->
            ZipInputStream(rawIn.buffered(256 * 1024)).use { zip ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val outFile = File(dest, name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        if (name == "SYMLINKS.txt") {
                            symlinksContent = zip.readBytes().decodeToString()
                        } else {
                            outFile.outputStream().use { out ->
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n <= 0) break
                                    out.write(buf, 0, n)
                                }
                            }
                            applyEntryMode(outFile, entry)
                        }
                    }
                    entryCount++
                    if (entryCount and 0x3F == 0) {
                        onProgress((entryCount.toDouble() / expectedEntries.toDouble()).toFloat().coerceIn(0f, 0.99f))
                    }
                    zip.closeEntry()
                }
            }
        }
        onProgress(1f)
        return symlinksContent
    }

    private fun applyEntryMode(file: File, entry: ZipEntry) {
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    private fun applyExecutablePermissions(root: File) {
        val execDirs = listOf(
            "bin",
            "libexec",
            "lib/apt",
            "lib/apt/solvers",
            "lib/apt/methods",
            "lib/apt/planners",
            "etc/termux/bootstrap",
        )
        execDirs.forEach { rel ->
            val dir = File(root, rel)
            if (!dir.isDirectory) return@forEach
            dir.listFiles()?.forEach { child ->
                if (child.isFile) {
                    child.setReadable(true, false)
                    child.setExecutable(true, false)
                }
            }
        }
        val apt = File(root, "bin/apt")
        val bash = File(root, "bin/bash")
        listOf(apt, bash).forEach { f ->
            if (f.exists()) f.setExecutable(true, false)
        }
    }

    private fun patchPrefixInTextFiles(root: File) {
        val oldPrefix = "/data/data/com.termux/files"
        val newPrefix = "/data/data/${TermuxConstants.TERMUX_PACKAGE_NAME}/files"
        if (oldPrefix == newPrefix) return

        val oldBytes = oldPrefix.encodeToByteArray()
        val newBytes = newPrefix.encodeToByteArray()

        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.toRelativeString(root).replace('\\', '/')
            if (!shouldPatchFile(rel)) return@forEach

            val bytes = try { file.readBytes() } catch (_: IOException) { return@forEach }
            val idx = indexOfSubarray(bytes, oldBytes)
            if (idx < 0) return@forEach

            val patched = replaceAll(bytes, oldBytes, newBytes)
            try {
                file.writeBytes(patched)
            } catch (_: IOException) { /* no-op */ }
        }
    }

    private fun shouldPatchFile(relPath: String): Boolean {
        if (relPath.startsWith("lib/")) return false
        if (relPath.startsWith("libexec/")) return false
        if (relPath.startsWith("share/")) return relPath.endsWith(".txt") ||
            relPath.endsWith(".conf") ||
            relPath.contains("/pkgconfig/") ||
            relPath.endsWith("changelog") ||
            relPath.contains("/man/")
        if (relPath.startsWith("bin/")) return isLikelyScript(File(relPath).name)
        if (relPath.startsWith("etc/")) return true
        if (relPath.startsWith("opt/")) return true
        if (relPath.startsWith("var/")) return true
        if (relPath.startsWith("include/")) return true
        return false
    }

    private fun isLikelyScript(fileName: String): Boolean =
        fileName.endsWith(".sh") ||
        fileName.endsWith(".py") ||
        fileName.endsWith(".pl") ||
        fileName.endsWith(".rb") ||
        fileName.endsWith(".awk") ||
        fileName.endsWith(".conf") ||
        fileName == "pkg"

    private fun indexOfSubarray(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        val last = haystack.size - needle.size
        if (last < 0) return -1
        outer@ for (i in 0..last) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun replaceAll(input: ByteArray, oldSeq: ByteArray, newSeq: ByteArray): ByteArray {
        if (oldSeq.size == newSeq.size) {
            val copy = input.copyOf()
            var i = 0
            val last = copy.size - oldSeq.size
            while (i <= last) {
                if (matchesAt(copy, i, oldSeq)) {
                    System.arraycopy(newSeq, 0, copy, i, newSeq.size)
                    i += newSeq.size
                } else i++
            }
            return copy
        }
        val out = ArrayList<Byte>(input.size + 64)
        var i = 0
        val last = input.size - oldSeq.size
        while (i < input.size) {
            if (i <= last && matchesAt(input, i, oldSeq)) {
                for (b in newSeq) out.add(b)
                i += oldSeq.size
            } else {
                out.add(input[i])
                i++
            }
        }
        return out.toByteArray()
    }

    private fun matchesAt(src: ByteArray, at: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) {
            if (src[at + j] != needle[j]) return false
        }
        return true
    }

    private fun applySymlinks(rawContent: String, root: File) {
        if (rawContent.isBlank()) return
        val oldPrefix = "/data/data/com.termux/files"
        val newPrefix = "/data/data/${TermuxConstants.TERMUX_PACKAGE_NAME}/files"

        rawContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val parts = trimmed.split("←")
            if (parts.size != 2) return@forEach

            val target = parts[0].let {
                if (it.startsWith(oldPrefix)) newPrefix + it.removePrefix(oldPrefix) else it
            }
            val linkRelative = parts[1].removePrefix("./")
            val linkFile = File(root, linkRelative)
            linkFile.parentFile?.mkdirs()
            linkFile.delete()
            try {
                Os.symlink(target, linkFile.absolutePath)
            } catch (_: Throwable) {
                /* ignore individual symlink failures; log path below */
            }
        }
    }

    fun shellEnvironment(): Map<String, String> {
        val prefix = prefixDir.absolutePath
        val home = homeDir.absolutePath
        return mapOf(
            "HOME" to home,
            "PREFIX" to prefix,
            "TMPDIR" to "$prefix/tmp",
            "PATH" to "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "$prefix/lib",
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "TERMUX_VERSION" to "0.118.3",
            "SHELL" to "$prefix/bin/bash",
        )
    }

    fun shellPath(): String = File(prefixDir, "bin/bash").absolutePath.takeIf { File(it).canExecute() }
        ?: "/system/bin/sh"
}
