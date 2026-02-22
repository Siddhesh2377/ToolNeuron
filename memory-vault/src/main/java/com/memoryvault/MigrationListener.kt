package com.memoryvault

interface MigrationListener {
    fun onMigrationStarted()
    fun onMigrationProgress(percent: Float)  // 0.0 to 1.0
    fun onMigrationComplete()
    fun onMigrationFailed(error: Exception)
}
