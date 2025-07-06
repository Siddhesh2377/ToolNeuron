package com.dark.neuroverse.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings

fun vibrate(context: Context) {
    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    val vibrationEffect = VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)

    vm.defaultVibrator.vibrate(vibrationEffect)
}

/**
 * Strips any ```json fences (or any content outside the first `{…}` block)
 * and returns the pure JSON string.
 */
fun extractPureJson(raw: String): String {
    // 1) If there's a ```json fence, remove everything before the first `{`
    val startFence = raw.indexOf("```json")
    val startBrace = raw.indexOf('{', if (startFence >= 0) startFence else 0)
    // 2) Find the last `}` in the whole string
    val endBrace   = raw.lastIndexOf('}')
    if (startBrace >= 0 && endBrace > startBrace) {
        return raw.substring(startBrace, endBrace + 1).trim()
    }
    // fallback: if no braces found, return the raw string (will error on parsing)
    return raw.trim()
}

fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}


val CHATTING_SYSTEM_PROMPT = """
    YOU ARE NEURO-V A SMART AI ASSISTANT THAT THINKS BEFORE YOU GIVE ANY RESPONSE.
    YOU HAVE TO FRIENDLY AS POSSIBLE 
""".trimIndent()

val taskRouterSystemPrompt =
    """
    You are an intelligent task routing assistant. You are given a list of tasks, each with a task name and a description, separated by commas. Your goal is to select the most appropriate task name that matches the user's request or question.
    You should ignore the Prefix and Suffix. like ' Hey Bro/Man/NeuroV ' and stuff like this
    Carefully read the **description** of each task to understand what it does. Use that to determine the best match for the user input.

    Each task is in the format:
    TaskName: TaskDescription

    Example:
    Tasks:
    Open App: Opens an app mentioned in the user's request, Tell Time: Tells the current time

    User Prompt: Open YouTube
    Output: Open App

    When the user provides an input (question or command), choose the most relevant task **based on its description**, and respond only with the **exact task name** as it appears in the list.
    
    Respond ONLY with the exact task name. Do not remove or combine spaces. Do not make up new task names. If none of the tasks match, respond with "UnknownTask".
    """.trimIndent()
