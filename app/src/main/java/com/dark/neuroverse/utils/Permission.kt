package com.dark.neuroverse.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import java.lang.String
import kotlin.Boolean
import kotlin.Exception
import kotlin.apply

object StoragePermissionHandler {

    fun hasManageExternalStoragePermission(): Boolean {
        return Environment.isExternalStorageManager()
    }

    fun requestManageExternalStoragePermission(activity: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                addCategory("android.intent.category.DEFAULT")
                setData(String.format("package:%s", activity.packageName).toUri())
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            activity.startActivity(intent)
        }
    }
}
