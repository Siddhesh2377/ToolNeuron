package com.dark.neuroverse.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust off-screen capture:
 * - Temporarily attaches ComposeView to window (alpha=0) so Compose actually renders
 * - Waits two frames after layout
 * - Then draws into a Bitmap and detaches
 */
suspend fun captureComposableToBitmap(
    context: Context,
    parentComposition: CompositionContext?,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
): Bitmap = withContext(Dispatchers.Main.immediate) {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)

    // Try to get a real root to attach to; fallback to a temp container if not an Activity
    val root: ViewGroup? = (context as? Activity)
        ?.window
        ?.decorView
        ?.findViewById(android.R.id.content)

    val host: FrameLayout = FrameLayout(context).apply {
        // keep it off-interaction and invisible but still laid out
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = FrameLayout.LayoutParams(w, h)
        alpha = 0f
    }

    val composeView = ComposeView(context).apply {
        parentComposition?.let { setParentCompositionContext(it) }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        layoutParams = FrameLayout.LayoutParams(w, h)

        // Optional: give a visible bg if your UI uses transparency
        setBackgroundColor(Color.WHITE)

        setContent { content() }
    }

    // Attach to window so Compose schedules real draws
    if (root != null) {
        root.addView(host)
        host.addView(composeView)
    }

    // Manual measure/layout
    val wSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
    val hSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
    host.measure(wSpec, hSpec)
    host.layout(0, 0, w, h)

    // Give Compose time to render first pixels
    withFrameNanos { /* frame 1 */ }
    withFrameNanos { /* frame 2 */ }

    // Snapshot
    val bmp = createBitmap(w, h).also { out ->
        val canvas = android.graphics.Canvas(out)
        composeView.draw(canvas)
    }

    // Clean up
    if (root != null) {
        host.removeAllViews()
        root.removeView(host)
    } else {
        composeView.disposeComposition()
    }

    bmp
}

/**
 * Uses runtime-measured size from the *on-screen* composable and captures the
 * *same content* off-screen (but attached invisibly so it actually renders).
 */
@Composable
fun ProjectedCapturable(
    modifier: Modifier = Modifier,
    captureKey: Any?,                 // change this to trigger
    captureWhen: Boolean = true,
    onCaptured: (Bitmap) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val parentComposition = LocalView.current.findViewTreeCompositionContext()

    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .onSizeChanged { sizePx = it }   // IntSize in px
    ) {
        content()
    }

    LaunchedEffect(captureKey, captureWhen, sizePx) {
        if (!captureWhen || parentComposition == null) return@LaunchedEffect
        val w = sizePx.width
        val h = sizePx.height
        if (w <= 0 || h <= 0) return@LaunchedEffect

        val bmp = captureComposableToBitmap(
            context = context,
            parentComposition = parentComposition,
            widthPx = w,
            heightPx = h,
            content = content
        )
        onCaptured(bmp)
    }
}
