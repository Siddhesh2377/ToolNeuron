package com.dark.tool_neuron.ui.screen.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.global.formatBackupTimestamp
import com.dark.tool_neuron.global.formatBytes
import com.dark.tool_neuron.ui.components.PasswordTextField
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.worker.SystemBackupManager

// ── Data Management Section ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DataManagementSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val backupOptions by viewModel.backupOptions.collectAsStateWithLifecycle()
    val backupSizeEstimate by viewModel.backupSizeEstimate.collectAsStateWithLifecycle()

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var deleteConfirmText by remember { mutableStateOf("") }

    // SAF launchers
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && backupPassword.isNotEmpty()) {
            viewModel.createBackup(uri, backupPassword)
            backupPassword = ""
            backupPasswordConfirm = ""
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && restorePassword.isNotEmpty()) {
            viewModel.restoreBackup(uri, restorePassword)
            restorePassword = ""
        }
    }

    // Auto-dismiss progress after completion, or restart after restore
    LaunchedEffect(backupProgress) {
        if (backupProgress is SystemBackupManager.BackupProgress.Complete) {
            if (showRestoreDialog) {
                // Restart process — Hilt singletons hold stale DB/DAO refs
                kotlinx.coroutines.delay(500)
                showRestoreDialog = false
                val activity = context as? Activity
                activity?.let {
                    val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    it.finishAffinity()
                    if (intent != null) it.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            } else {
                kotlinx.coroutines.delay(2000)
                viewModel.clearBackupProgress()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
        // Progress indicator
        AnimatedVisibility(
            visible = backupProgress != null && backupProgress !is SystemBackupManager.BackupProgress.Complete
                    && backupProgress !is SystemBackupManager.BackupProgress.Error,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Row(
                    modifier = Modifier.padding(Standards.CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = when (val p = backupProgress) {
                            is SystemBackupManager.BackupProgress.Starting -> "Starting..."
                            is SystemBackupManager.BackupProgress.Collecting -> p.component
                            is SystemBackupManager.BackupProgress.Processing -> {
                                val stage = if (p.stage.isNotEmpty()) "${p.stage} " else ""
                                "${stage}${(p.progress * 100).toInt()}%"
                            }
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Status messages
        val progressStatus = backupProgress
        if (progressStatus is SystemBackupManager.BackupProgress.Complete) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Text(
                    "Operation completed successfully",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(Standards.CardPadding)
                )
            }
        }
        if (progressStatus is SystemBackupManager.BackupProgress.Error) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Text(
                    progressStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Standards.CardPadding)
                )
            }
        }

        // --- Green Backup Card ---
        Surface(
            onClick = { showBackupDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.CloudUpload, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Create encrypted backup of all app data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Restore Card ---
        Surface(
            onClick = { showRestoreDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.CloudDownload, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Restore from Backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Restore from encrypted backup file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Red Delete All Card ---
        Surface(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.TrashX, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Delete All Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Permanently delete all app data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // ── Dialogs ──

    if (showBackupDialog) {
        BackupDialog(
            backupPassword = backupPassword,
            onPasswordChange = { backupPassword = it },
            backupPasswordConfirm = backupPasswordConfirm,
            onPasswordConfirmChange = { backupPasswordConfirm = it },
            backupOptions = backupOptions,
            onOptionsChange = { viewModel.updateBackupOptions(it) },
            backupSizeEstimate = backupSizeEstimate,
            onEstimateSize = { viewModel.estimateBackupSize() },
            onConfirm = {
                showBackupDialog = false
                val timestamp = formatBackupTimestamp()
                backupLauncher.launch("toolneuron_backup_$timestamp.tnbackup")
            },
            onDismiss = {
                showBackupDialog = false
                backupPassword = ""
                backupPasswordConfirm = ""
            }
        )
    }

    if (showRestoreDialog) {
        RestoreDialog(
            restorePassword = restorePassword,
            onPasswordChange = { restorePassword = it },
            onConfirm = {
                restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onDismiss = {
                showRestoreDialog = false
                restorePassword = ""
            }
        )
    }

    if (showDeleteDialog) {
        DeleteAllDataDialog(
            deleteConfirmText = deleteConfirmText,
            onConfirmTextChange = { deleteConfirmText = it },
            onConfirm = {
                showDeleteDialog = false
                deleteConfirmText = ""
                viewModel.deleteAllData()
            },
            onDismiss = {
                showDeleteDialog = false
                deleteConfirmText = ""
            }
        )
    }
}

// ── Backup Dialog ──

@Composable
private fun BackupDialog(
    backupPassword: String,
    onPasswordChange: (String) -> Unit,
    backupPasswordConfirm: String,
    onPasswordConfirmChange: (String) -> Unit,
    backupOptions: SystemBackupManager.BackupOptions,
    onOptionsChange: (SystemBackupManager.BackupOptions) -> Unit,
    backupSizeEstimate: SystemBackupManager.BackupSizeEstimate?,
    onEstimateSize: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onEstimateSize() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.CloudUpload, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = {
            Text("Create Backup", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "Set a password to encrypt your backup. You'll need this password to restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordTextField(
                    value = backupPassword,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false
                )
                PasswordTextField(
                    value = backupPasswordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = "Confirm Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false,
                    isError = backupPasswordConfirm.isNotEmpty() && backupPassword != backupPasswordConfirm
                )

                Spacer(modifier = Modifier.height(Standards.SpacingXs))

                SwitchRow(
                    title = "Include RAG files",
                    checked = backupOptions.includeRagFiles,
                    onCheckedChange = { checked ->
                        onOptionsChange(backupOptions.copy(includeRagFiles = checked))
                    }
                )

                SwitchRow(
                    title = "Include AI Models",
                    checked = backupOptions.includeModelFiles,
                    onCheckedChange = { checked ->
                        onOptionsChange(backupOptions.copy(includeModelFiles = checked))
                    }
                )

                if (backupOptions.includeModelFiles && backupSizeEstimate != null) {
                    val models = backupSizeEstimate.modelBreakdown
                    if (models.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = Standards.SpacingSm),
                            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXxs)
                        ) {
                            models.forEach { model ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        model.modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.canBackup)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (model.canBackup) formatBytes(model.sizeBytes)
                                        else model.reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.canBackup)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                backupSizeEstimate?.let { estimate ->
                    Text(
                        "Estimated size: ${formatBytes(estimate.totalSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = backupPassword.length >= 4 && backupPassword == backupPasswordConfirm
            ) {
                Text("Create Backup", color = MaterialTheme.colorScheme.tertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}

// ── Restore Dialog ──

@Composable
private fun RestoreDialog(
    restorePassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text("Restore from Backup", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "This will replace all current data with the backup. The app will restart after restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                PasswordTextField(
                    value = restorePassword,
                    onValueChange = onPasswordChange,
                    label = "Backup Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = restorePassword.length >= 4
            ) {
                Text("Select Backup File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}

// ── Delete All Data Dialog ──

@Composable
private fun DeleteAllDataDialog(
    deleteConfirmText: String,
    onConfirmTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.TrashX, null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text("Delete All Data", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "This will permanently delete all chats, memories, personas, RAG data, and settings. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Type DELETE to confirm",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = deleteConfirmText,
                    onValueChange = onConfirmTextChange,
                    label = { Text("Type DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = deleteConfirmText.isNotEmpty() && deleteConfirmText != "DELETE"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = deleteConfirmText == "DELETE"
            ) {
                Text("Delete Everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}
