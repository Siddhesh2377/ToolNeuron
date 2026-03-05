package com.dark.tool_neuron.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.icons.TnIcons
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

    // Navigate when setup completes
    LaunchedEffect(setupComplete) {
        if (setupComplete) {
            delay(400)
            onSetupComplete()
        }
    }

    val isDownloading = selectedOption != null && selectedOption != SetupOption.POWER_MODE
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
                .padding(horizontal = 24.dp),
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You can Minimize the app, Will Notify You",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(24.dp))

                        // Progress bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Indeterminate for extracting/processing
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    } else {
                        Text(
                            "Welcome User",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Choose Your Setup!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Options list with staggered animation
            val options = listOf(
                SetupOption.TEXT to "Text",
                SetupOption.TEXT_UNCENSORED to "Text Uncensored",
                SetupOption.TEXT_TTS to "Text + TTS",
                SetupOption.IMAGE_GEN to "Image Gen",
                SetupOption.POWER_MODE to "Power Mode ( SKIP )"
            )

            options.forEachIndexed { index, (option, label) ->
                key(option) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = Motion.interactive()
                        ) + fadeIn(Motion.content())
                    ) {
                        SetupOptionCard(
                            label = label,
                            isSelected = selectedOption == option,
                            isDownloading = isDownloading,
                            enabled = selectedOption == null,
                            onClick = { viewModel.selectOption(option) }
                        )
                    }

                    if (index < options.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Error message
            AnimatedVisibility(
                visible = downloadError != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = downloadError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.retryDownload() }) {
                        Text("Retry")
                    }
                }
            }

            // Restore from Backup section
            if (!isDownloading) {
                Spacer(Modifier.height(24.dp))
                RestoreFromBackupCard(viewModel = viewModel)
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = when (progress) {
                        is SystemBackupManager.BackupProgress.Starting -> "Restoring..."
                        is SystemBackupManager.BackupProgress.Collecting -> progress.component
                        is SystemBackupManager.BackupProgress.Processing -> "Restoring ${(progress.progress * 100).toInt()}%"
                        is SystemBackupManager.BackupProgress.Complete -> "Restore complete!"
                        else -> "Restoring..."
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            Spacer(Modifier.height(8.dp))
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your backup password, then select the backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Backup Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ==================== Option Card ====================

@Composable
private fun SetupOptionCard(
    label: String,
    isSelected: Boolean,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isDownloading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
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
        shape = RoundedCornerShape(Standards.CardCornerRadius),
        color = backgroundColor,
        enabled = enabled && !isDownloading
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = contentColor
            )

            if (isSelected && isDownloading) {
                Icon(
                    imageVector = TnIcons.RadioButton,
                    contentDescription = "Downloading",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = TnIcons.Download,
                    contentDescription = "Download",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
