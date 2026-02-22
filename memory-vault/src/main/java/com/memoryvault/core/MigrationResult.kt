package com.memoryvault.core

sealed class MigrationResult {
    data class Success(
        val blocksReEncrypted: Int,
        val backupLocation: String
    ) : MigrationResult()

    data class Failure(
        val error: Exception,
        val backupLocation: String?
    ) : MigrationResult()
}
