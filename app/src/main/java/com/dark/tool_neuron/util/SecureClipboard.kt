package com.dark.tool_neuron.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SecureClipboard {

    private const val AUTO_CLEAR_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun buildClipData(label: String, text: CharSequence): ClipData {
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle()
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }
        return clip
    }

    fun copy(context: Context, label: String, text: CharSequence, autoClearMs: Long = AUTO_CLEAR_MS) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val payload = text.toString()
        cm.setPrimaryClip(buildClipData(label, payload))
        scheduleAutoClear(cm, payload, autoClearMs)
    }

    private fun scheduleAutoClear(cm: ClipboardManager, expected: String, delayMs: Long) {
        if (delayMs <= 0) return
        scope.launch {
            delay(delayMs)
            if (currentText(cm) == expected) {
                clear(cm)
            }
        }
    }

    fun clear(cm: ClipboardManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cm.clearPrimaryClip()
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun currentText(cm: ClipboardManager): String? {
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }
}
