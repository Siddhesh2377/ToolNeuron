package com.dark.tool_neuron.util

import java.io.File

object VlmPaths {

    const val VLM_DIR = "vlm"

    fun isMmprojFileName(fileName: String): Boolean =
        fileName.contains("mmproj", ignoreCase = true)

    fun hasMmprojFile(repoFilePaths: Iterable<String>): Boolean =
        repoFilePaths.any { it.endsWith(".gguf", ignoreCase = true) && isMmprojFileName(it) }

    fun vlmFolderName(repoPath: String): String {
        val base = repoPath.substringAfterLast('/').ifBlank { repoPath }
        return sanitize(base)
    }

    fun vlmFolder(modelsDir: File, repoPath: String): File =
        File(File(modelsDir, VLM_DIR), vlmFolderName(repoPath))

    fun isInsideVlmFolder(absolutePath: String, modelsDir: File): Boolean {
        val vlmRoot = File(modelsDir, VLM_DIR).absolutePath + File.separator
        return absolutePath.startsWith(vlmRoot)
    }

    fun colocatedMmproj(baseModelFile: File): File? {
        val dir = baseModelFile.parentFile ?: return null
        val listing = dir.listFiles() ?: return null
        return listing
            .filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            .firstOrNull { isMmprojFileName(it.name) }
    }

    fun listColocatedMmprojs(baseModelFile: File): List<File> {
        val dir = baseModelFile.parentFile ?: return emptyList()
        val listing = dir.listFiles() ?: return emptyList()
        return listing
            .filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            .filter { isMmprojFileName(it.name) }
            .sortedBy { it.name.lowercase() }
    }

    fun resolveProjector(baseModelFile: File, preferredFileName: String?): File? {
        val candidates = listColocatedMmprojs(baseModelFile)
        if (candidates.isEmpty()) return null
        if (!preferredFileName.isNullOrBlank()) {
            candidates.firstOrNull { it.name.equals(preferredFileName, ignoreCase = true) }?.let { return it }
        }
        return candidates.first()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
