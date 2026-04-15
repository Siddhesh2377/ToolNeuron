package com.dark.tool_neuron.ui.screen.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.PasswordTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.HardwareScanner
import com.dark.tool_neuron.global.PerformanceMode
import com.dark.tool_neuron.viewmodel.SetupOption
import com.dark.tool_neuron.viewmodel.SetupViewModel
import com.dark.tool_neuron.worker.SystemBackupManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val viewModel: SetupViewModel = viewModel()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val setupComplete by viewModel.setupComplete.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()
    val primaryModelId by viewModel.primaryModelId.collectAsStateWithLifecycle()
    val showPerformancePicker by viewModel.showPerformancePicker.collectAsStateWithLifecycle()

    // Navigate when setup completes
    LaunchedEffect(setupComplete) {
        if (setupComplete) {
            delay(400)
            onSetupComplete()
        }
    }

    val isDownloading = selectedOption != null && selectedOption != SetupOption.POWER_MODE

    AnimatedContent(
        targetState = showPerformancePicker,
        transitionSpec = {
            fadeIn(Motion.entrance()) togetherWith fadeOut(Motion.exit())
        },
        label = "setupPhase"
    ) { showPerfPicker ->
        if (showPerfPicker) {
            PerformancePickerContent(viewModel = viewModel)
        } else {
            SetupOptionsContent(
                viewModel = viewModel,
                selectedOption = selectedOption,
                downloadStates = downloadStates,
                downloadError = downloadError,
                primaryModelId = primaryModelId,
                isDownloading = isDownloading
            )
        }
    }
}

// ── Setup Options Phase ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupOptionsContent(
    viewModel: SetupViewModel,
    selectedOption: SetupOption?,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    downloadError: String?,
    primaryModelId: String?,
    isDownloading: Boolean
) {
    val downloadState = primaryModelId?.let { downloadStates[it] }

    val progress = when (downloadState) {
        is ModelDownloadService.DownloadState.Downloading -> downloadState.progress
        is ModelDownloadService.DownloadState.Extracting -> -1f
        is ModelDownloadService.DownloadState.Processing -> -1f
        is ModelDownloadService.DownloadState.Success -> 1f
        else -> 0f
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Header section
            AnimatedContent(
                targetState = isDownloading,
                transitionSpec = {
                    (fadeIn(Motion.entrance()) + slideInVertically(
                        initialOffsetY = { -it / 4 },
                        animationSpec = Motion.content()
                    )) togetherWith fadeOut(Motion.exit())
                },
                label = "header"
            ) { downloading ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (downloading) {
                        Text(
                            "Downloading...",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Standards.SpacingSm))
                        Text(
                            "You can Minimize the app, Will Notify You",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(Standards.SpacingXl))

                        // Progress bar with droplets
                        if (progress >= 0f) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DropletProgressBar(
                                    progress = progress,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    barColor = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                Spacer(Modifier.width(Standards.SpacingMd))
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Indeterminate for extracting/processing
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    } else {
                        Text(
                            "Welcome User",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Standards.SpacingSm))
                        Text(
                            "Choose Your Setup!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(Standards.SpacingXxl))

            // Options list with staggered animation
            data class SetupCard(
                val option: SetupOption,
                val icon: androidx.compose.ui.graphics.vector.ImageVector,
                val title: String,
                val subtitle: String
            )

            val options = listOf(
                SetupCard(SetupOption.TEXT, TnIcons.Sparkles, "Text Generation", "LFM2 350M · ~200 MB"),
                SetupCard(SetupOption.TEXT_TTS, TnIcons.Volume, "Text + Speech", "LFM2 + Supertonic TTS · ~460 MB"),
                SetupCard(SetupOption.IMAGE_GEN, TnIcons.Photo, "Image Generation", "AbsoluteReality · ~1.1 GB"),
                SetupCard(SetupOption.POWER_MODE, TnIcons.Bolt, "Power Mode", "Set up later in Store")
            )

            options.forEachIndexed { index, card ->
                key(card.option) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(Motion.content())
                    ) {
                        SetupOptionCard(
                            icon = card.icon,
                            title = card.title,
                            subtitle = card.subtitle,
                            isSelected = selectedOption == card.option,
                            isDownloading = isDownloading,
                            enabled = selectedOption == null,
                            onClick = { viewModel.selectOption(card.option) }
                        )
                    }

                    if (index < options.lastIndex) {
                        Spacer(Modifier.height(Standards.SpacingSm))
                    }
                }
            }

            // Error message
            AnimatedVisibility(
                visible = downloadError != null,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column(
                    modifier = Modifier.padding(top = Standards.SpacingLg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = downloadError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Standards.SpacingSm))
                    TextButton(onClick = { viewModel.retryDownload() }) {
                        Text("Retry")
                    }
                }
            }

            // Restore from Backup section
            if (!isDownloading) {
                Spacer(Modifier.height(Standards.SpacingXl))
                RestoreFromBackupCard(viewModel = viewModel)
            }
        }
    }
}

// ── Performance Picker Phase ──

@Composable
private fun PerformancePickerContent(viewModel: SetupViewModel) {
    val selectedMode by viewModel.selectedPerformanceMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val clusterInfo = remember {
        try {
            val profile = HardwareScanner.scan(context)
            val topo = profile.cpuTopology
            if (topo.scanSucceeded) {
                buildString {
                    if (topo.primeCoreCount > 0) append("${topo.primeCoreCount}P")
                    if (topo.performanceCoreCount > 0) {
                        if (isNotEmpty()) append("+")
                        append("${topo.performanceCoreCount}P")
                    }
                    if (topo.efficiencyCoreCount > 0) {
                        if (isNotEmpty()) append("+")
                        append("${topo.efficiencyCoreCount}E")
                    }
                    append(" cores")
                }
            } else "${topo.totalPhysicalCores} cores"
        } catch (_: Exception) { null }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Optimize Performance",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Standards.SpacingSm))
            Text(
                "Choose how your device runs AI models",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            clusterInfo?.let {
                Spacer(Modifier.height(Standards.SpacingXs))
                Text(
                    "Detected: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(Standards.SpacingXxl))

            val modes = listOf(
                Triple(PerformanceMode.PERFORMANCE, TnIcons.Gauge, "Maximum speed, higher battery usage"),
                Triple(PerformanceMode.BALANCED, TnIcons.Adjustments, "Good speed with reasonable battery life"),
                Triple(PerformanceMode.POWER_SAVING, TnIcons.Shield, "Slower but saves battery")
            )

            modes.forEachIndexed { index, (mode, icon, description) ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 80L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(Motion.content())
                ) {
                    PerformanceModeCard(
                        mode = mode,
                        icon = icon,
                        description = description,
                        isSelected = selectedMode == mode,
                        onClick = { viewModel.selectPerformanceMode(mode) }
                    )
                }

                if (index < modes.lastIndex) {
                    Spacer(Modifier.height(Standards.SpacingSm))
                }
            }

            Spacer(Modifier.height(Standards.SpacingXl))

            FilledTonalButton(
                onClick = { viewModel.confirmPerformanceMode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}

// ==================== Restore from Backup ====================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestoreFromBackupCard(viewModel: SetupViewModel) {
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    val restoreProgress by viewModel.restoreProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Restart process after successful restore — Hilt singletons hold stale DB/DAO refs
    LaunchedEffect(restoreProgress) {
        if (restoreProgress is SystemBackupManager.BackupProgress.Complete) {
            kotlinx.coroutines.delay(1000)
            val activity = context as? android.app.Activity
            activity?.let {
                val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
                    ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                it.finishAffinity()
                if (intent != null) it.startActivity(intent)
                Runtime.getRuntime().exit(0)
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && restorePassword.isNotEmpty()) {
            viewModel.restoreFromBackup(uri, restorePassword)
            restorePassword = ""
            showRestoreDialog = false
        }
    }

    // Show progress or the restore button
    val progress = restoreProgress
    if (progress != null && progress !is SystemBackupManager.BackupProgress.Error) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = when (progress) {
                        is SystemBackupManager.BackupProgress.Starting -> "Restoring..."
                        is SystemBackupManager.BackupProgress.Collecting -> progress.component
                        is SystemBackupManager.BackupProgress.Processing -> "Restoring ${(progress.progress * 100).toInt()}%"
                        is SystemBackupManager.BackupProgress.Complete -> "Restore complete!"
                        is SystemBackupManager.BackupProgress.Error -> "Restoring..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        Surface(
            onClick = { showRestoreDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                Icon(
                    TnIcons.Restore, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Restore from Backup",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Error from previous attempt
        if (progress is SystemBackupManager.BackupProgress.Error) {
            Spacer(Modifier.height(Standards.SpacingSm))
            Text(
                text = progress.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Restore dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                restorePassword = ""
            },
            icon = { Icon(TnIcons.Restore, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore from Backup", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                    Text(
                        "Enter your backup password, then select the backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PasswordTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = "Backup Password",
                        modifier = Modifier.fillMaxWidth(),
                        showToggle = false
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = restorePassword.length >= 4
                ) { Text("Select Backup File") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restorePassword = ""
                }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(Standards.RadiusXl)
        )
    }
}

// ==================== Performance Mode Card ====================

@Composable
private fun PerformanceModeCard(
    mode: PerformanceMode,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = Motion.state(),
        label = "perfBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = Motion.state(),
        label = "perfContent"
    )

    val label = when (mode) {
        PerformanceMode.PERFORMANCE -> "Performance"
        PerformanceMode.BALANCED -> "Balanced"
        PerformanceMode.POWER_SAVING -> "Power Saver"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusXl),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = TnIcons.RadioButton,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== Option Card ====================

@Composable
private fun SetupOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isDownloading -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = Motion.state(),
        label = "optionBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected && isDownloading -> MaterialTheme.colorScheme.onPrimaryContainer
            !enabled && !isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = Motion.state(),
        label = "optionContent"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusXl),
        color = backgroundColor,
        enabled = enabled && !isDownloading
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected && isDownloading) MaterialTheme.colorScheme.primary
                       else contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            if (isSelected && isDownloading) {
                Icon(
                    imageVector = TnIcons.RadioButton,
                    contentDescription = "Downloading",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== Droplet Progress Bar ====================

private data class Droplet(
    val x: Float,
    val startY: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    var y: Float = startY,
    var life: Float = 1f
)

@Composable
private fun DropletProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barColor: Color,
    trackColor: Color
) {
    val droplets = remember { mutableListOf<Droplet>() }
    var tick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            tick += 0.016f
        }
    }

    Canvas(modifier = modifier) {
        val barHeight = 6.dp.toPx()
        val barRadius = barHeight / 2f
        val barY = size.height / 2f - barHeight / 2f
        val filledWidth = size.width * progress.coerceIn(0f, 1f)

        // ── Track ──
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barY),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barRadius)
        )

        // ── Filled bar ──
        if (filledWidth > 0f) {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(0f, barY),
                size = Size(filledWidth, barHeight),
                cornerRadius = CornerRadius(barRadius)
            )
        }

        // ── Spawn droplets at the right edge of the filled bar ──
        if (progress > 0.01f && progress < 1f) {
            val spawnX = filledWidth
            val spawnY = barY + barHeight

            // Spawn new droplets periodically
            val spawnChance = if (sin(tick * 8f) > 0.3f) 0.25f else 0.08f
            if (Random.nextFloat() < spawnChance && droplets.size < 6) {
                droplets.add(
                    Droplet(
                        x = spawnX + Random.nextFloat() * 8f - 4f,
                        startY = spawnY,
                        speed = 1.2f + Random.nextFloat() * 1.5f,
                        radius = 2f + Random.nextFloat() * 2.5f,
                        alpha = 0.6f + Random.nextFloat() * 0.4f
                    )
                )
            }
        }

        // ── Update & draw droplets ──
        val iterator = droplets.iterator()
        while (iterator.hasNext()) {
            val d = iterator.next()
            d.y += d.speed
            d.life -= 0.025f

            if (d.life <= 0f || d.y > size.height + 10f) {
                iterator.remove()
                continue
            }

            // Droplet shrinks as it falls
            val currentRadius = d.radius * d.life.coerceIn(0f, 1f)
            val currentAlpha = (d.alpha * d.life).coerceIn(0f, 1f)

            drawCircle(
                color = barColor.copy(alpha = currentAlpha),
                radius = currentRadius,
                center = Offset(d.x, d.y)
            )

            // Small highlight on top of droplet
            if (currentRadius > 2f) {
                drawCircle(
                    color = Color.White.copy(alpha = currentAlpha * 0.4f),
                    radius = currentRadius * 0.35f,
                    center = Offset(d.x - currentRadius * 0.2f, d.y - currentRadius * 0.25f)
                )
            }
        }
    }
}
