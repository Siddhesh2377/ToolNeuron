package com.dark.tool_neuron.repo

import com.dark.tool_neuron.model.HFRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class ValidationResult {
    data class Valid(val ggufFileCount: Int) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
    data object Checking : ValidationResult()
}

@Singleton
class RepositoryValidator @Inject constructor() {

    suspend fun validate(repo: HFRepository): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val infoConn = (URL("https://huggingface.co/api/models/${repo.repoPath}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 15_000
            }
            val infoCode = try { infoConn.responseCode } finally { infoConn.disconnect() }
            if (infoCode != 200) {
                return@withContext when (infoCode) {
                    404 -> ValidationResult.Invalid("Repository not found")
                    401, 403 -> ValidationResult.Invalid("Access denied")
                    else -> ValidationResult.Invalid("HTTP $infoCode")
                }
            }

            val filesConn = (URL("https://huggingface.co/api/models/${repo.repoPath}/tree/main").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            val filesBody = try {
                if (filesConn.responseCode != 200)
                    return@withContext ValidationResult.Invalid("Failed to list files")
                filesConn.inputStream.bufferedReader().use { it.readText() }
            } finally { filesConn.disconnect() }

            val arr = JSONArray(filesBody)
            var ggufCount = 0
            for (i in 0 until arr.length()) {
                val path = arr.getJSONObject(i).optString("path", "")
                if (path.endsWith(".gguf", ignoreCase = true)) ggufCount++
            }

            if (ggufCount == 0) ValidationResult.Invalid("No GGUF files found")
            else ValidationResult.Valid(ggufCount)
        } catch (e: Exception) {
            ValidationResult.Invalid(e.message ?: "Unknown error")
        }
    }
}
