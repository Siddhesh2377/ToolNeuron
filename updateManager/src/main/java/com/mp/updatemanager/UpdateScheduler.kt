package com.mp.updatemanager

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object UpdateScheduler {

    fun scheduleTestUpdateCheck(context: Context, delayInSeconds: Long) {
        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            15, TimeUnit.MINUTES // Minimum allowed by WorkManager
        )
            .setInitialDelay(delayInSeconds, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "test_update_check",
            ExistingPeriodicWorkPolicy.REPLACE, // replace if already running
            workRequest
        )
    }

    fun scheduleDailyUpdateCheck(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun calculateInitialDelay(): Long {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, nextMidnight).toMillis()
    }
}
