package com.dark.task_manager.tasks.foreground.application_operator

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import org.json.JSONObject
import kotlin.math.min

class ApplicationOperator(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Application Operator",
            description = "Opens an Android app by app Name, RULES is NO NEED TO ADD PACKAGE NAME FOR CALLING THIS TOOL, YOU JUST HAVE TO PROVIDE THE APP NAME",
            args = """
                { app_name: String }
            """.trimIndent(),
            taskType = TaskType.FOREGROUND
        )
    }

    override fun onStart(any: Any) {
        Log.d(getTaskInfo().taskName, "ApplicationTask started")
    }

    override fun onRun(any: Any): Any {
        Log.d(getTaskInfo().taskName, "Apps List = ${listApps(context)}")
        val input = any.toString().lowercase()
        val args: JSONObject = any as JSONObject
        val appNames = args.getString("app_name")

        if (appNames.isEmpty()) {
            Log.w(getTaskInfo().taskName, "No app names detected in input: $input")
            return JSONObject().put("result", "No app names detected in input: $input")
        }

        val installedApps = listApps(context)

        val matchedApp = fuzzyFindApp(installedApps, appNames)
        if (matchedApp != null) {
            launchApp(context, matchedApp.packageName) {
                Log.e(getTaskInfo().taskName, "Error launching app ${matchedApp.appName}: $it")
            }
        } else {
            Log.w(getTaskInfo().taskName, "No match found for app name: $appNames")
        }


        Log.d(getTaskInfo().taskName, "ApplicationTask completed")
        return JSONObject().put("result", "Success").put("type", "application")
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

    private fun extractAppNames(input: String): List<String> {
        val openIndex = input.indexOf("open")
        if (openIndex == -1) return emptyList()

        val afterOpen = input.substring(openIndex + 4).trim()

        // Split on "and", "&", "then", ",", and normalize
        return afterOpen
            .lowercase()
            .replace("&", ",")
            .replace(" and ", ",")
            .replace(" then ", ",")
            .replace(",", ", ")
            .replace(" or ", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }


    private fun fuzzyFindApp(apps: List<AppInfo>, name: String): AppInfo? {
        val cleanedName = name.trim().lowercase()
        return apps.minByOrNull {
            levenshtein(it.appName.lowercase(), cleanedName)
        }?.takeIf {
            // Optional: threshold filter
            levenshtein(it.appName.lowercase(), cleanedName) <= 3
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
