package com.dark.task_manager.tasks.foreground.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import org.json.JSONObject
import java.lang.String
import kotlin.Any
import kotlin.Exception
import kotlin.text.lowercase
import kotlin.text.trimIndent


class FileManager(context: Context): TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "File Manager",
            description = "USE TO HANDLE FILE OPERATIONS e.g -> CREATE FILE, DELETE FILE, RENAME FILE, MOVE FILE, SHARE FILE",
            args = """
                    {
                        "file_name": String,                   // Required. Name of the file (with extension if applicable)
                        "action": enum("CREATE", "DELETE", "RENAME", "MOVE", "SHARE"), // Required. File operation
                        "data": {
                            "type": enum("USER_DEFINED", "AUTO_GENERATED"), // Defines data source
                            "content": String?,             // Optional. Required only if type is USER_DEFINED
                            "format": String?               // Optional. e.g., "txt", "json", "pdf" — helps in random generation
                        },
                        "new_name": String?,                // Optional. Required if action = RENAME
                        "target_path": String?              // Optional. Required if action = MOVE
                    }
                    """.trimIndent(),
            taskType = TaskType.FOREGROUND
        )
    }

    override fun onStart(any: Any) {

    }

    override fun onRun(any: Any): Any {
        val input = any.toString().lowercase()

        Log.d(getTaskInfo().taskName, "Input: $input")

        val args: JSONObject = any as JSONObject
        processRequest(args)

        return JSONObject().put("result", "Success").put("type", "file_manager")
    }


    override fun onStop() {
        
    }

    fun processRequest(args: JSONObject){
        val fileName = args.getString("file_name")
        val action = args.getString("action")

        when(action){
            "CREATE" -> {}
            "DELETE" -> {}
            "RENAME" -> {}
            "MOVE" -> {}
            "SHARE" -> {}
        }


    }

}