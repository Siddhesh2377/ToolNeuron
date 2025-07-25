package com.dark.plugins.sys.plugins

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo
import com.dark.plugins.sys.uiAction.fuzzyFindApp
import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(
    val appName: String, val packageName: String, val icon: Drawable
)

class AppIOPlugin(val context: Context) : PluginApi(context) {

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo("AppIOPlugin", "App IO Operation")
    }

    override fun onCreate(data: Any) {
        super.onCreate(data)
        val tasks = data as JSONObject

        when(tasks.getString("action")){
            "open" -> {
                val tempPKG = tasks.getString("packageName")

                AppIOTasks.launchApp(
                    context,
                    fuzzyFindApp(AppIOTasks.listAppsTask(context), tempPKG)?.packageName
                        ?: tempPKG
                ) { err ->
                    Log.e("AppIOPlugin", err)
                }
            }

            "close" -> {
                Log.d("AppIOPlugin", "close")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AppIOPlugin", "onDestroy")
    }
}


internal object AppIOTasks {
    fun checkForAppUpdatesTask(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://play.google.com/".toUri()
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.d("PlayStore", "PlayStore opened successfully from app.")
        } catch (e: ActivityNotFoundException) {
            Log.e("PlayStore", "PlayStore could not be opened from app.")
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun listAppsTask(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0)
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
}