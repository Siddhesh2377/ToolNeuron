package com.dark.tool_neuron.repo

import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.network.HuggingFaceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ValidationResult {
    data class Valid(val ggufFileCount: Int, val label: String = "GGUF") : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
    object Checking : ValidationResult()
}

class RepositoryValidator {

    private val api = HuggingFaceClient.api
    private val validationCache = mutableMapOf<String, ValidationResult>()

    /**
     * Validates a HuggingFace repository by checking:
     * 1. Repository exists (getRepoInfo returns success)
     * 2. Repository contains GGUF files (getRepoFiles contains .gguf files)
     */
    suspend fun validateRepository(repo: HFModelRepository): ValidationResult = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            validationCache[repo.id]?.let { cachedResult ->
                if (cachedResult !is ValidationResult.Checking) {
                    return@withContext cachedResult
                }
            }

            // Step 1: Check if repository exists
            val repoInfoResponse = api.getRepoInfo(repo.repoPath)

            if (!repoInfoResponse.isSuccessful) {
                val errorMessage = when (repoInfoResponse.code()) {
                    404 -> "Repository not found (404)"
                    401, 403 -> "Access denied (${repoInfoResponse.code()})"
                    else -> "HTTP error (${repoInfoResponse.code()})"
                }
                return@withContext ValidationResult.Invalid(errorMessage).also {
                    validationCache[repo.id] = it
                }
            }

            // Step 2: Check for model files based on repo type
            val filesResponse = api.getRepoFiles(repo.repoPath)

            if (!filesResponse.isSuccessful) {
                return@withContext ValidationResult.Invalid("Failed to fetch files").also {
                    validationCache[repo.id] = it
                }
            }

            val files = filesResponse.body() ?: emptyList()

            val (matchingFiles, fileLabel) = when (repo.modelType) {
                ModelType.SD -> {
                    files.filter { it.path.endsWith(".zip", ignoreCase = true) } to "ZIP"
                }
                else -> {
                    files.filter { it.path.endsWith(".gguf", ignoreCase = true) } to "GGUF"
                }
            }

            if (matchingFiles.isEmpty()) {
                return@withContext ValidationResult.Invalid("No $fileLabel files found").also {
                    validationCache[repo.id] = it
                }
            }

            // Success
            ValidationResult.Valid(matchingFiles.size, fileLabel).also {
                validationCache[repo.id] = it
            }

        } catch (e: Exception) {
            ValidationResult.Invalid("Error: ${e.message ?: "Unknown error"}").also {
                validationCache[repo.id] = it
            }
        }
    }

}
