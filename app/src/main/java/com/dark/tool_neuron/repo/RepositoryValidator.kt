package com.dark.tool_neuron.repo

import com.dark.tool_neuron.model.HFRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ValidationResult {
    data class Valid(val ggufFileCount: Int) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
    data object Checking : ValidationResult()
}

@Singleton
class RepositoryValidator @Inject constructor(
    private val hfApi: HuggingFaceApi,
) {

    suspend fun validate(repo: HFRepository): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val infoStatus = hfApi.probe(hfApi.modelInfoUrl(repo.repoPath)).getOrNull()
                ?: return@withContext ValidationResult.Invalid("Network error")
            if (infoStatus != 200) {
                return@withContext when (infoStatus) {
                    404 -> ValidationResult.Invalid("Repository not found")
                    401, 403 -> ValidationResult.Invalid("Access denied")
                    429 -> ValidationResult.Invalid("Rate limited — try again later")
                    else -> ValidationResult.Invalid("HTTP $infoStatus")
                }
            }

            val tree = hfApi.fetchJsonArray(hfApi.modelTreeUrl(repo.repoPath)).getOrNull()
                ?: return@withContext ValidationResult.Invalid("Failed to list files")

            var ggufCount = 0
            for (i in 0 until tree.length()) {
                val path = tree.getJSONObject(i).optString("path", "")
                if (path.endsWith(".gguf", ignoreCase = true)) ggufCount++
            }

            if (ggufCount == 0) ValidationResult.Invalid("No GGUF files found")
            else ValidationResult.Valid(ggufCount)
        } catch (e: Exception) {
            ValidationResult.Invalid(e.message ?: "Unknown error")
        }
    }
}
