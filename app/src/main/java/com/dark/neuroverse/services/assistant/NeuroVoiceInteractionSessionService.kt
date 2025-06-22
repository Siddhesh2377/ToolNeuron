package com.dark.neuroverse.services.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dark.ai_manager.ai.local.Neuron
import com.dark.neuroverse.compose.screens.assistant.NeuroVScreen
import kotlinx.coroutines.delay

/**
 * A VoiceInteractionSessionService that hosts a Compose-based AssistantScreen.
 * Optimized for memory efficiency by minimizing object retention and redundant logs.
 */
class NeuroVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Neuron.init() {
            Log.d(TAG, "onNewSession")
        }
        return NeuroSession(this)
    }

    companion object {
        private const val TAG = "NeuroVoiceService"
    }
}

/**
 * The NeuroSession hosts the Compose UI inside the voice interaction session.
 * We maintain only one lifecycle owner and saved state registry to reduce overhead.
 */
class NeuroSession(context: Context) : VoiceInteractionSession(context) {
    private val lifecycleOwner = SimpleLifecycleOwner()
    private val savedStateRegistryOwner = SimpleSavedStateRegistryOwner(lifecycleOwner)
    private var composeRoot: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Session onCreate")
        // Restore saved state (if any) without passing a bundle for now
        savedStateRegistryOwner.performRestore(null)

        // Create a root container
        val root = FrameLayout(context).apply {
            // Attach lifecycle and saved state owners to this view hierarchy
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        }


        // Create and configure ComposeView
        val composeView = ComposeView(context).apply {
            // Dispose composition when detached to free resources
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val close = remember { mutableStateOf(false) }

                LaunchedEffect(close.value) {
                    if (close.value) {
                        delay(520L)
                        finish()
                    }
                }

                AnimatedContent(
                    targetState = close.value,
                    transitionSpec = {
                        (slideInVertically(
                            tween(
                                durationMillis = 500,
                                easing = FastOutLinearInEasing
                            )
                        ) + fadeIn()).togetherWith(
                            fadeOut(tween(durationMillis = 500, easing = FastOutSlowInEasing))
                        )
                    },
                    label = "Animated Content"
                ) { isClosing ->
                    if (!isClosing) {
                        NeuroVScreen(onClickOutside = {
                            close.value = true
                        })
                    }
                }


            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Add ComposeView to root and set as content
        root.addView(composeView)
        setContentView(root)
        composeRoot = root

        // Move lifecycle to CREATED
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Log.d(TAG, "Session UI created")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Move lifecycle to RESUMED
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        Log.d(TAG, "Session onShow: Resumed")
    }

    override fun onHide() {
        super.onHide()
        // Move lifecycle to PAUSED
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        Log.d(TAG, "Session onHide: Paused")
    }

    override fun onDestroy() {
        Log.d(TAG, "Session onDestroy: Cleaning up")
        // Move lifecycle to DESTROYED
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        Neuron.unloadAllModels()
        // Remove ComposeView to release resources
        composeRoot?.removeAllViews()
        composeRoot = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        // Do NOT call super.onHandleAssist(…)—that is what triggers the HTML dump.
        // Just swallow it:
        Log.d(TAG, "onHandleAssist (ignored).")
    }

    companion object {
        private const val TAG = "NeuroSession"
    }
}

/**
 * A lightweight LifecycleOwner residing solely in memory,
 * tracking its LifecycleRegistry state.
 */
private class SimpleLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.INITIALIZED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
        Log.d("SimpleLifecycleOwner", "Event: $event, State: ${lifecycleRegistry.currentState}")
    }
}

/**
 * Simplified SavedStateRegistryOwner that observes the lifecycle of another owner.
 * Minimizes overhead by using a single SavedStateRegistryController.
 */
private class SimpleSavedStateRegistryOwner(
    lifecycleOwnerForState: LifecycleOwner
) : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    override val lifecycle: Lifecycle = lifecycleOwnerForState.lifecycle

    init {
        Log.d("SimpleSavedStateOwner", "Initialized with lifecycle: ${lifecycle.currentState}")
    }

    fun performRestore(savedState: Bundle?) {
        controller.performRestore(savedState)
        Log.d("SimpleSavedStateOwner", "performRestore called")
    }

    @Deprecated("performSave is typically auto-managed.")
    fun performSave(outBundle: Bundle) {
        controller.performSave(outBundle)
        Log.d("SimpleSavedStateOwner", "performSave called")
    }
}
