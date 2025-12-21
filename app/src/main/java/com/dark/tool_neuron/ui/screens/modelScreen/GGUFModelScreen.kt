package com.dark.tool_neuron.ui.screens.modelScreen

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import com.dark.tool_neuron.activity.formatBytes
import com.dark.tool_neuron.model.GGUFModels
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.llm_model.ModelScreenViewModel
import com.dark.tool_neuron.viewModel.modelScreen.OnlineModelStoreViewModel
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.ModelProvider
import com.mp.ai_engine.models.llm_models.toGGUFModel
import com.mp.ai_engine.workers.DownloadState
import java.io.File

@Composable
fun GGUFModelScreen(
    viewModel: OnlineModelStoreViewModel = viewModel()
) {
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            availableModels.isEmpty() -> {
                EmptyState()
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    items(
                        items = availableModels,
                        key = { it.modelFileLink } // Use download URL as unique key
                    ) { cloudModel ->
                        // Check if model is installed
                        val isInstalled = installedModels.any {
                            it.modelName == cloudModel.modelName
                        }

                        // Get download state
                        val downloadState = downloadsState.getDownload(cloudModel.modelFileLink)

                        // Get compatibility
                        val compatibility = getModelCompatibility(context, cloudModel.toGGUFModel(
                            File("")))

                        ModelCard(
                            model = cloudModel,
                            compatibility = compatibility,
                            isInstalled = isInstalled,
                            downloadState = downloadState,
                            onDownload = {
                                viewModel.downloadModel(cloudModel)
                            },
                            onCancelDownload = {
                                viewModel.cancelDownload(cloudModel.modelFileLink)
                            },
                            onDelete = {
                                // Find the installed model ID
                                val installedModel = installedModels.find {
                                    it.modelName == cloudModel.modelName
                                }
                                installedModel?.let {
                                    viewModel.deleteModel(it.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Model card with download/install functionality
 */
@Composable
fun ModelCard(
    model: CloudModel,
    compatibility: ModelCompatibility,
    isInstalled: Boolean,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(20.dp))
        ) {
            // Header - Model Name & Info
            ModelHeader(model = model)

            Spacer(modifier = Modifier.height(rDP(16.dp)))

//            // Tags
//            if (model.metaData["tags"]?.isNotEmpty() == true) {
//                ModelTags(tags = model.metaData["tags"]!!.split(","))
//                Spacer(modifier = Modifier.height(rDP(16.dp)))
//            }

            // Compatibility Badge
            CompatibilityBadge(compatibility = compatibility)

            Spacer(modifier = Modifier.height(rDP(16.dp)))

            // Quick Stats
            ModelQuickStats(model = model)

            // Show Details Toggle
            Spacer(modifier = Modifier.height(rDP(12.dp)))
            TextButton(
                onClick = { showDetails = !showDetails },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (showDetails) "Hide Details" else "Show Details",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                    )
                )
                Icon(
                    imageVector = if (showDetails) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(18.dp))
                )
            }

            // Expanded Details
            if (showDetails) {
                Spacer(modifier = Modifier.height(rDP(12.dp)))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(rDP(16.dp)))
                DetailedSpecs(model = model.toGGUFModel(File("")), compatibility = compatibility)
            }

            Spacer(modifier = Modifier.height(rDP(16.dp)))

            // Action Section
            ModelActionSection(
                downloadState = downloadState,
                isInstalled = isInstalled,
                compatibility = compatibility,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDelete = onDelete
            )
        }
    }
}

/**
 * Header section with model name and description
 */
@Composable
private fun ModelHeader(model: CloudModel) {
    Column {
        Text(
            text = model.modelName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (model.modelDescription.isNotEmpty()) {
            Spacer(modifier = Modifier.height(rDP(6.dp)))
            Text(
                text = model.modelDescription,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(rDP(10.dp)))

        Row(
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Architecture badge
            val architecture = model.metaData["architecture"] ?: "LLAMA"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(rDP(6.dp)))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = rDP(10.dp), vertical = rDP(4.dp))
            ) {
                Text(
                    text = architecture.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "•",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Text(
                text = model.modelFileSize,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Quick statistics row
 */
@Composable
private fun ModelQuickStats(model: CloudModel) {
    val ctxSize = model.metaData["ctxSize"]?.toIntOrNull() ?: 4096
    val gpuLayers = model.metaData["gpu-layers"]?.toIntOrNull() ?: 0
    val temp = model.metaData["temp"]?.toFloatOrNull() ?: 0.7f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDP(10.dp))
    ) {
        QuickStatCard(
            icon = Icons.Default.TextFields,
            label = "Context",
            value = formatNumber(ctxSize),
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            icon = Icons.Outlined.Layers,
            label = "GPU Layers",
            value = gpuLayers.toString(),
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            icon = Icons.Default.Speed,
            label = "Temp",
            value = temp.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Action section - download/install/delete buttons
 */
@Composable
private fun ModelActionSection(
    downloadState: DownloadState?,
    isInstalled: Boolean,
    compatibility: ModelCompatibility,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    when (downloadState) {
        is DownloadState.Downloading -> {
            DownloadProgressSection(
                progress = downloadState.progressPercent.toFloat(),
                downloadedBytes = downloadState.downloadedBytes,
                totalBytes = downloadState.totalBytes,
                onCancel = onCancelDownload
            )
        }

        is DownloadState.Installing -> {
            InstallingSection()
        }

        is DownloadState.Completed, null -> {
            if (isInstalled) {
                InstalledSection(onDelete = onDelete)
            } else {
                DownloadButton(
                    compatibility = compatibility,
                    onDownload = onDownload
                )
            }
        }

        is DownloadState.Failed -> {
            ErrorSection(
                errorMessage = downloadState.error,
                onRetry = onDownload
            )
        }

        is DownloadState.Cancelled -> {
            DownloadButton(
                compatibility = compatibility,
                onDownload = onDownload
            )
        }

        else -> { /* Idle state */ }
    }
}

/**
 * Download button
 */
@Composable
private fun DownloadButton(
    compatibility: ModelCompatibility,
    onDownload: () -> Unit
) {
    Button(
        onClick = onDownload,
        modifier = Modifier
            .fillMaxWidth()
            .height(rDP(52.dp)),
        shape = RoundedCornerShape(rDP(12.dp)),
        enabled = compatibility.rating != CompatibilityRating.INCOMPATIBLE,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = rDP(0.dp)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(rDP(20.dp))
        )
        Spacer(modifier = Modifier.width(rDP(10.dp)))
        Text(
            text = if (compatibility.rating == CompatibilityRating.INCOMPATIBLE)
                "Incompatible with Device"
            else "Download Model",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
            ),
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Installing indicator
 */
@Composable
private fun InstallingSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(12.dp)),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(rDP(16.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(rDP(20.dp)),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = rDP(2.dp)
            )
            Spacer(modifier = Modifier.width(rDP(12.dp)))
            Text(
                text = "Installing Model...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Download progress section
 */
@Composable
private fun DownloadProgressSection(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${progress.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (totalBytes > 0) {
                    Text(
                        text = "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(rDP(8.dp))
                .clip(RoundedCornerShape(rDP(4.dp))),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(rDP(12.dp)))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(rDP(20.dp))
            )
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text("Cancel Download")
        }
    }
}

data class ModelCompatibility(
    val score: Int, // 0-100
    val rating: CompatibilityRating,
    val ramRequirement: String,
    val storageRequirement: String,
    val deviceRam: String,
    val availableStorage: String,
    val recommendations: List<String>,
    val warnings: List<String>
)

enum class CompatibilityRating {
    EXCELLENT, GOOD, FAIR, POOR, INCOMPATIBLE
}

fun getModelCompatibility(context: Context, model: GGUFDatabaseModel): ModelCompatibility {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    val availableRamGB = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)

    // Parse model size (assuming format like "4.2GB", "500MB")
    val modelSizeGB = parseModelSize(model.modelFileSize)

    // Estimate RAM requirement (model size + overhead)
    val estimatedRamGB = modelSizeGB * 1.5 // 50% overhead for processing

    // Get available storage
    val availableStorageGB = context.filesDir.usableSpace / (1024.0 * 1024.0 * 1024.0)

    // Calculate compatibility score
    var score = 100
    val recommendations = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // Check RAM
    when {
        estimatedRamGB > totalRamGB -> {
            score -= 60
            warnings.add("Model requires more RAM than available")
        }

        estimatedRamGB > availableRamGB * 0.8 -> {
            score -= 30
            warnings.add("May experience memory pressure")
            recommendations.add("Close other apps before running")
        }

        estimatedRamGB > availableRamGB * 0.5 -> {
            score -= 15
            recommendations.add("Moderate RAM usage expected")
        }

        else -> {
            recommendations.add("Excellent RAM availability")
        }
    }

    // Check storage
    when {
        modelSizeGB > availableStorageGB -> {
            score -= 40
            warnings.add("Insufficient storage space")
        }

        modelSizeGB > availableStorageGB * 0.8 -> {
            score -= 20
            warnings.add("Low storage space")
        }
    }

    // Check context size
    when {
        model.ctxSize > 8192 -> {
            score -= 10
            recommendations.add("Large context size - may be slower")
        }

        model.ctxSize > 4096 -> {
            recommendations.add("Standard context size")
        }
    }

    // Check GPU layers
    if (model.gpuLayers > 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recommendations.add("GPU acceleration available")
            score += 10
        } else {
            warnings.add("GPU layers set but may not be supported")
            score -= 5
        }
    }

    // Determine rating
    val rating = when {
        score >= 85 -> CompatibilityRating.EXCELLENT
        score >= 70 -> CompatibilityRating.GOOD
        score >= 50 -> CompatibilityRating.FAIR
        score >= 30 -> CompatibilityRating.POOR
        else -> CompatibilityRating.INCOMPATIBLE
    }

    return ModelCompatibility(
        score = score.coerceIn(0, 100),
        rating = rating,
        ramRequirement = "${"%.1f".format(estimatedRamGB)} GB",
        storageRequirement = model.modelFileSize,
        deviceRam = "${"%.1f".format(totalRamGB)} GB",
        availableStorage = "${"%.1f".format(availableStorageGB)} GB",
        recommendations = recommendations,
        warnings = warnings
    )
}

fun parseModelSize(sizeStr: String): Double {
    val cleaned = sizeStr.trim().uppercase()
    return when {
        cleaned.endsWith("GB") -> cleaned.removeSuffix("GB").trim().toDoubleOrNull() ?: 0.0
        cleaned.endsWith("MB") -> (cleaned.removeSuffix("MB").trim().toDoubleOrNull()
            ?: 0.0) / 1024.0

        else -> 0.0
    }
}

@Composable
private fun DownloadProgressSection(
    progress: Float,  // This is 0-100
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${progress.toInt()}%",  // Already 0-100
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDP(12.dp))
                .clip(RoundedCornerShape(rDP(6.dp)))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PixelProgressBar(
                progress = progress / 100f,  // Convert to 0-1 for the progress bar
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(rDP(12.dp)))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(rDP(20.dp))
            )
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text("Cancel Download")
        }
    }
}

@Composable
private fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_animation")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width * progress
        val pixelSize = 4.dp.toPx()
        val pixelGap = 1.dp.toPx()
        val totalPixelSize = pixelSize + pixelGap

        val numPixelsX = (barWidth / totalPixelSize).toInt()
        val numPixelsY = (size.height / totalPixelSize).toInt().coerceAtLeast(1)

        for (y in 0 until numPixelsY) {
            for (x in 0 until numPixelsX) {
                val xPos = x * totalPixelSize
                val yPos = y * totalPixelSize

                val normalizedX = x.toFloat() / numPixelsX.coerceAtLeast(1)

                val alpha = if (normalizedX > shimmerOffset - 0.2f &&
                    normalizedX < shimmerOffset) {
                    1f
                } else {
                    0.7f
                }

                drawRect(
                    color = Color(0xFF6750A4).copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(xPos, yPos),
                    size = androidx.compose.ui.geometry.Size(pixelSize, pixelSize)
                )
            }
        }
    }
}

@Composable
private fun InstalledSection(onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(rDP(12.dp)),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(rDP(16.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(rDP(20.dp))
                )
                Spacer(modifier = Modifier.width(rDP(8.dp)))
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(rDP(48.dp))
                .clip(RoundedCornerShape(rDP(12.dp)))
                .background(MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ErrorSection(
    errorMessage: String, onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp)),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier.padding(rDP(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(rDP(20.dp))
                )
                Spacer(modifier = Modifier.width(rDP(8.dp)))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(rDP(20.dp))
            )
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text("Retry Download")
        }
    }
}

@Composable
fun ModelTypeChip(modelType: String) {
    val color = when (modelType) {
        "TXT" -> MaterialTheme.colorScheme.primaryContainer
        "VLM" -> MaterialTheme.colorScheme.secondaryContainer
        "EMBED" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when (modelType) {
        "TXT" -> MaterialTheme.colorScheme.onPrimaryContainer
        "VLM" -> MaterialTheme.colorScheme.onSecondaryContainer
        "EMBED" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(color)
            .padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))
    ) {
        Text(
            text = modelType,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)),
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(rDP(64.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(rDP(16.dp)))
            Text(
                text = "No models available",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Models will appear here when available",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(6.dp)))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
            .padding(horizontal = rDP(10.dp), vertical = rDP(5.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun QuickStatCard(
    icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(rDP(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(6.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(rDP(20.dp))
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CompatibilityBadge(compatibility: ModelCompatibility) {
    val (bgColor, iconColor, icon, text) = when (compatibility.rating) {
        CompatibilityRating.EXCELLENT -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary,
            Icons.Default.CheckCircle,
            "Excellent"
        )

        CompatibilityRating.GOOD -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.secondary,
            Icons.Default.ThumbUp,
            "Good"
        )

        CompatibilityRating.FAIR -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Info,
            "Fair"
        )

        CompatibilityRating.POOR -> Quadruple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.error,
            Icons.Default.Warning,
            "Poor"
        )

        CompatibilityRating.INCOMPATIBLE -> Quadruple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.error,
            Icons.Default.Error,
            "Incompatible"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(bgColor)
            .padding(horizontal = rDP(14.dp), vertical = rDP(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(rDP(18.dp)),
                tint = iconColor
            )
            Text(
                text = "Device Compatibility: $text",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DetailedSpecs(model: GGUFDatabaseModel, compatibility: ModelCompatibility) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
    ) {
        // System Requirements
        Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
            Text(
                text = "System Requirements",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            SystemRequirementRow(
                label = "RAM",
                required = compatibility.ramRequirement,
                available = compatibility.deviceRam,
                isSufficient = compatibility.score >= 50
            )
            SystemRequirementRow(
                label = "Storage",
                required = compatibility.storageRequirement,
                available = compatibility.availableStorage,
                isSufficient = parseModelSize(compatibility.storageRequirement) <= parseModelSize(
                    compatibility.availableStorage
                )
            )
        }

        // Sampling Parameters (Power User Section)
        Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
            Text(
                text = "Sampling Configuration",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Grid layout for parameters
            Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    ParameterItem("Temperature", model.temp.toString(), Modifier.weight(1f))
                    ParameterItem("Top-P", model.topP.toString(), Modifier.weight(1f))
                    ParameterItem("Top-K", model.topK.toString(), Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    ParameterItem("Min-P", model.minP.toString(), Modifier.weight(1f))
                    ParameterItem("Max Tokens", formatNumber(model.maxTokens), Modifier.weight(1f))
                    ParameterItem("Mirostat", model.mirostat.toString(), Modifier.weight(1f))
                }
            }
        }

        // Performance Settings
        Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
            Text(
                text = "Performance Settings",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                PerformanceToggle("MMAP", model.useMMAP, Modifier.weight(1f))
                PerformanceToggle("MLOCK", model.useMLOCK, Modifier.weight(1f))
            }
        }

        // Recommendations & Warnings
        if (compatibility.recommendations.isNotEmpty() || compatibility.warnings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(10.dp))) {
                if (compatibility.recommendations.isNotEmpty()) {
                    Text(
                        text = "Recommendations",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    compatibility.recommendations.forEach { rec ->
                        InfoRow(
                            icon = Icons.Default.Lightbulb,
                            text = rec,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (compatibility.warnings.isNotEmpty()) {
                    Text(
                        text = "Warnings",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    compatibility.warnings.forEach { warning ->
                        InfoRow(
                            icon = Icons.Default.Warning,
                            text = warning,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun SystemRequirementRow(
    label: String, required: String, available: String, isSufficient: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(rDP(12.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDP(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$required / $available",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)),
                fontWeight = FontWeight.Bold,
                color = if (isSufficient) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Icon(
                imageVector = if (isSufficient) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(rDP(16.dp)),
                tint = if (isSufficient) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ParameterItem(
    label: String, value: String, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(rDP(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = rSp(MaterialTheme.typography.bodyLarge.fontSize)),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PerformanceToggle(
    label: String, enabled: Boolean, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(
                if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(rDP(10.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)),
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(rDP(18.dp)),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InfoRow(
    icon: ImageVector, text: String, color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(color.copy(alpha = 0.1f))
            .padding(rDP(10.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(rDP(16.dp)),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)),
            color = color
        )
    }
}

// Helper data class for Quadruple
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)