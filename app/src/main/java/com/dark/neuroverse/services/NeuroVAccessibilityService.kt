package com.dark.neuroverse.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dark.plugins.sys.uiAction.UiCommandQueue
import kotlinx.coroutines.*
import org.json.JSONObject

class NeuroVAccessibilityService : AccessibilityService() {

    private val tag = this::class.java.simpleName
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Accessibility service initialized")

        serviceScope.launch {
            while (isActive) {
                val cmd = UiCommandQueue.hasCommand().takeIf { it }?.let { UiCommandQueue.peek() }
                val root = rootInActiveWindow
                if (cmd != null && root != null) {
                    processCommand(cmd, root)
                    UiCommandQueue.poll() // remove *after* processing
                } else {
                    if (cmd != null) Log.w(tag, "Root is null, will retry later")
                }
                delay(300)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName ?: return
        if (pkg == "com.android.vending") {
            Log.d(tag, "Window changed in: $pkg")

            serviceScope.launch {
                Log.d(tag, "Accessibility service connected and ready.")
                while (UiCommandQueue.hasCommand()) {
                    Log.d(tag, "Accessibility service Executing...")
                    val cmd = UiCommandQueue.poll()
                    if (cmd != null) {
                        Log.d(tag, "Accessibility service Executing... $cmd")
                        val root = rootInActiveWindow
                        if (root != null) {
                            processCommand(cmd, root)
                        } else {
                            Log.w(tag, "Root is null, skipping command")
                        }
                    }else{
                        Log.w(tag, "No command to execute")
                    }
                    delay(300)
                }
            }
        }
    }

    private suspend fun processCommand(cmdJson: JSONObject, root: AccessibilityNodeInfo) {
        val action = cmdJson.optString("action")
        Log.d(tag, "Processing command: $cmdJson")

        when (action) {

            "scrollDown" -> {
                val scrollableNode = findScrollableNode(root)
                val times = cmdJson.optInt("times", 1)

                if (scrollableNode != null) {
                    repeat(times) { index ->
                        delay(500)
                        val success = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        Log.d(tag, "Scroll #${index + 1}: ${if (success) "✅ success" else "❌ failed"}")
                    }
                } else {
                    Log.w(tag, "No scrollable node found")
                }
            }

            "clickButton" -> {
                val buttonText = cmdJson.optString("buttonText")
                val fallbackText = cmdJson.optString("fallBackText", "")
                val parentDepth = cmdJson.optInt("parents", 0)

                Log.d(tag, "Looking for button: '$buttonText' | Fallback: '$fallbackText'")

                var node = findNodeByText(root, buttonText)
                if (node == null && fallbackText.isNotEmpty()) {
                    Log.d(tag, "Trying fallback text...")
                    node = findNodeByText(root, fallbackText)
                }

                if (node != null) {
                    var targetNode = node
                    repeat(parentDepth) {
                        targetNode = targetNode?.parent
                    }

                    val clicked = targetNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked == true) {
                        Log.d(tag, "✅ Clicked on: ${targetNode.text ?: "no-text"}")
                    } else {
                        Log.w(tag, "❌ Found but not clickable: ${targetNode?.text}")
                    }
                } else {
                    Log.w(tag, "❌ No matching node for buttonText/fallbackText")
                }
            }

            else -> Log.w(tag, "Unknown action: $action")
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, targetText: String, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.trim() ?: ""
        if (text.equals(targetText, ignoreCase = true)) return node

        for (i in 0 until node.childCount) {
            val result = findNodeByText(node.getChild(i), targetText, depth + 1)
            if (result != null) return result
        }

        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            Log.d(tag, "Checking child: ${node.getChild(i)} IsScrollable ? ${node.isScrollable}")
            val scrollable = findScrollableNode(node.getChild(i))
            if (scrollable != null) return scrollable
        }
        return null
    }

    override fun onInterrupt() {
        Log.w(tag, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(tag, "Accessibility service destroyed")
    }
}
