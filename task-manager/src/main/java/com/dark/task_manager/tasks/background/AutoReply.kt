package com.dark.task_manager.tasks.background

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dark.ai_manager.ai.local.Neuron
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import com.dark.task_manager.services.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoReply(context: Context) : TaskApi(context) {

    private var lastMessageId = ""
    private var lastSender = ""
    private var lastMessage = ""
    private var isGenerating = false

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Auto Reply",
            description = "Automatically replies to WhatsApp messages when user is unavailable.",
            systemPrompt = "",
            taskType = TaskType.BACKGROUND
        )
    }

    override fun onStart(any: Any) {

    }

    override fun onRun(any: Any) {
        if (isGenerating) return
        if (any is Pair<*, *> && any.first is NotificationListener && any.second is StatusBarNotification) {
            onReceived(any.first as NotificationListener, any.second as StatusBarNotification)
        }
    }

    private fun onReceived(listener: NotificationListener, sbn: StatusBarNotification) {
        if (isGenerating) return
        CoroutineScope(Dispatchers.IO).launch {
            Neuron.updateSystemPrompt("""
                You are an AI assistant that sends automatic replies when the user is unavailable.
                Respond briefly, politely, and relevant to the incoming message.
                Keep all replies under 1-2 short sentences.
                Never generate long paragraphs, filler, or unrelated text.
                Assume the user is busy, driving, or in a meeting.
            """.trimIndent())
        }

        val packageName = sbn.packageName ?: return
        if (!packageName.contains("whatsapp", ignoreCase = true)) return

        val extras = sbn.notification.extras ?: return
        val sender = extras.getString("android.title") ?: return
        val message = extras.getString("android.text") ?: return
        val key = sbn.key ?: return

        // Avoid replying to self or duplicates
        if (sender.equals("You", ignoreCase = true)) return
        if (key == lastMessageId && sender == lastSender && message == lastMessage) return

        Log.d("AutoReply", "🔔 New Message from $sender: $message")

        val actions = sbn.notification.actions ?: return
        lastMessageId = key
        lastSender = sender
        lastMessage = message

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue

            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey.lowercase().contains("reply")) {

                    CoroutineScope(Dispatchers.IO).launch {
                        isGenerating = true
                        try {
                            val aiPrompt = "Here is a message for which a short auto-reply is needed: $message"
                            val aiResponse = Neuron.generateResponseStreaming(aiPrompt) {}

                            val cleanedText = aiResponse.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                            val finalReply = cleanedText.ifBlank { "I'm currently busy, will reply later." }

                            Log.d("AutoReply", "✅ AI Auto-Reply: $finalReply")

                            val replyIntent = action.actionIntent
                            val replyBundle = Bundle().apply {
                                putCharSequence(remoteInput.resultKey, finalReply)
                            }
                            val intent = Intent()
                            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, replyBundle)

                            replyIntent.send(listener, 0, intent)
                            listener.cancelNotification(sbn.key)

                        } catch (e: Exception) {
                            Log.e("AutoReply", "❌ Failed to send AI reply", e)
                        } finally {
                            isGenerating = false
                        }
                    }
                    return
                }
            }
        }
    }
}
