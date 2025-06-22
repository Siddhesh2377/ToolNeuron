package com.dark.task_manager.tasks.application_operator

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import kotlin.math.min

class ApplicationOperator(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Application Operator",
            description = "Opens an app mentioned in the user's request",
            systemPrompt = """
                System: You are an intent parser. Whenever the user’s message matches the pattern

                open <app_name>

                respond exactly with:

                1:<app_name>

                Do not add any extra words, punctuation, or formatting. The match should be case-insensitive and capture any app name following the word “open.”
            """.trimIndent()
        )
    }

    override fun onStart() {
        Log.d(getTaskInfo().taskName, "ApplicationTask started")
    }

    override fun onRun(any: Any) {
        val input = any.toString()
        val appInfo = findAppByName(context, input)
        if (appInfo != null) {
            launchApp(context, appInfo.packageName) {
                Log.e(getTaskInfo().taskName, "Error launching app: $it")
            }
        }
        Log.d(getTaskInfo().taskName, "ApplicationTask running")
    }

    override fun onStop() {
        Log.d(getTaskInfo().taskName, "ApplicationTask stopped")
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun listApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return apps.map { resolveInfo ->
            val appName = resolveInfo.loadLabel(packageManager).toString()
            val packageName = resolveInfo.activityInfo.packageName
            val icon = resolveInfo.loadIcon(packageManager)
            AppInfo(appName, packageName, icon)
        }
    }

    fun launchApp(context: Context, packageName: String, onError: (err: String) -> Unit) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            onError("App not found")
        }
    }

    fun findAppByName(context: Context, inputName: String): AppInfo? {
        val apps = listApps(context)
        val cleanedInput = inputName.trim().lowercase()

        // First try exact or contains match
        val directMatch = apps.find {
            it.appName.equals(cleanedInput, true) || it.appName.lowercase().contains(cleanedInput)
        }

        if (directMatch != null) return directMatch

        // Then use Levenshtein distance for approximate match
        return apps.minByOrNull { levenshtein(it.appName.lowercase(), cleanedInput) }
    }

    private fun levenshtein(lhs: String, rhs: String): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        val cost = Array(lhsLength + 1) { IntArray(rhsLength + 1) }

        for (i in 0..lhsLength) cost[i][0] = i
        for (j in 0..rhsLength) cost[0][j] = j

        for (i in 1..lhsLength) {
            for (j in 1..rhsLength) {
                val editCost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                cost[i][j] = min(
                    min(cost[i - 1][j] + 1, cost[i][j - 1] + 1),
                    cost[i - 1][j - 1] + editCost
                )
            }
        }
        return cost[lhsLength][rhsLength]
    }
}
