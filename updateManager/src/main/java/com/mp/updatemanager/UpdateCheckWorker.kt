package com.mp.updatemanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateCheckWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jsonUrl =
            "https://raw.githubusercontent.com/Siddhesh2377/NeuroVerse/refs/heads/fresh-new/repo/AppUpdate.json"

        return try {
            UpdateChecker.checkForUpdate(applicationContext, jsonUrl)
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Error checking update", e)
            Result.retry()
        }
    }
}
