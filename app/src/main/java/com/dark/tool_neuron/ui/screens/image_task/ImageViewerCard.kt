package com.dark.tool_neuron.ui.screens.image_task

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.util.ImageExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

@Composable
fun ImageViewerCard(
    bitmap: Bitmap,
    title: String,
    saveNamePrefix: String = "tool_neuron",
    showUpscale: Boolean = false,
    onUpscaleClick: (() -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullscreen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savingAs by remember { mutableStateOf(false) }
    var pendingSaveAsBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val outUri = uri ?: run {
            savingAs = false
            pendingSaveAsBitmap = null
            return@rememberLauncherForActivityResult
        }
        val pending = pendingSaveAsBitmap ?: bitmap
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                writeBitmapToUri(context, pending, outUri)
            }
            savingAs = false
            pendingSaveAsBitmap = null
            toast(
                context,
                if (result.isSuccess) "Saved image"
                else "Save failed: ${result.exceptionOrNull()?.message}",
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = tnShapes.card,
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(dimens.iconMd),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${bitmap.width}×${bitmap.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ZoomableBitmap(
                bitmap = bitmap,
                onTapToFullscreen = { fullscreen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tnShapes.cardSmall)
                    .background(MaterialTheme.colorScheme.surface),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                ActionTextButton(
                    onClickListener = {
                        saving = true
                        scope.launch {
                            val result = ImageExport.saveBitmapToGallery(
                                context = context,
                                bitmap = bitmap,
                                displayName = "${saveNamePrefix}_${System.currentTimeMillis()}",
                            )
                            saving = false
                            toast(
                                context,
                                if (result.isSuccess) "Saved to Pictures/ToolNeuron"
                                else "Save failed: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    },
                    icon = TnIcons.Download,
                    text = if (saving) "Saving…" else "Save Photos",
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                )

                ActionTextButton(
                    onClickListener = {
                        savingAs = true
                        pendingSaveAsBitmap = bitmap
                        saveAsLauncher.launch("${saveNamePrefix}_${System.currentTimeMillis()}.png")
                    },
                    icon = TnIcons.Download,
                    text = if (savingAs) "Saving…" else "Save as…",
                    enabled = !savingAs,
                    modifier = Modifier.weight(1f),
                )

                ActionTextButton(
                    onClickListener = { fullscreen = true },
                    icon = TnIcons.Eye,
                    text = "View",
                    modifier = Modifier.weight(1f),
                )

                if (showUpscale && onUpscaleClick != null) {
                    ActionTextButton(
                        onClickListener = onUpscaleClick,
                        icon = TnIcons.Sparkles,
                        text = "Upscale 4×",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (fullscreen) {
        FullscreenImageDialog(
            bitmap = bitmap,
            onDismiss = { fullscreen = false },
        )
    }
}

private fun writeBitmapToUri(context: Context, bitmap: Bitmap, uri: Uri): Result<Unit> = runCatching {
    context.contentResolver.openOutputStream(uri).use { out ->
        requireNotNull(out) { "openOutputStream returned null" }
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            throw IllegalStateException("Bitmap.compress returned false")
        }
    }
}

@Composable
private fun ZoomableBitmap(
    bitmap: Bitmap,
    onTapToFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = {
                            if (scale <= 1.01f) onTapToFullscreen()
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )

        if (scale > 1.01f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${"%.1f".format(scale)}×",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = TnIcons.X,
                            contentDescription = "Reset zoom",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                ZoomableBitmap(
                    bitmap = bitmap,
                    onTapToFullscreen = onDismiss,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(
                            imageVector = TnIcons.X,
                            contentDescription = "Close",
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
