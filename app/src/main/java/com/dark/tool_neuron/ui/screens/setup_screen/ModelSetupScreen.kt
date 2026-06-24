package com.dark.tool_neuron.ui.screens.setup_screen

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.model_store.ModelImportTypePicker
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import kotlinx.coroutines.delay

private enum class SetupPath(val label: String) {
    Packs("Packs"),
    Restore("Restore"),
    Custom("Custom")
}

private data class SetupPack(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val SETUP_PACKS = listOf(
    SetupPack(
        id = ModelStoreViewModel.PACK_CHAT_ONLY,
        title = "Chat only",
        subtitle = "LFM2 350M. Around 200 MB. Works on low-RAM phones.",
        icon = TnIcons.Leaf,
    ),
    SetupPack(
        id = ModelStoreViewModel.PACK_CHAT_VOICE,
        title = "Chat with voice",
        subtitle = "LFM2 350M plus speech in and out. Around 310 MB.",
        icon = TnIcons.Mic,
    ),
    SetupPack(
        id = ModelStoreViewModel.PACK_LARGE_CHAT_VOICE,
        title = "Larger chat with voice",
        subtitle = "Qwen3 0.6B plus speech in and out. Around 530 MB.",
        icon = TnIcons.Sparkles,
    ),
)

@Composable
fun ModelSetupScreen(
    innerPadding: PaddingValues,
    onPackSelected: (packId: String) -> Unit,
    setupBusy: Boolean = false,
    onOpenStore: () -> Unit,
    onRestoreBackup: (uri: Uri) -> Unit,
    onLocalImport: (uri: Uri, name: String, size: Long, type: ProviderType) -> Unit,
    onSkip: () -> Unit,
) {
    val dimens = LocalDimens.current
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf(SetupPath.Packs) }
    var selectedPack by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Triple<Uri, String, Long>?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            var name = "model.gguf"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            pendingImport = Triple(uri, name, size)
        }
    }

    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestoreBackup(uri)
    }

    LaunchedEffect(Unit) { delay(80); visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = dimens.screenPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 }
            ) {
                Column {
                    Icon(
                        imageVector = TnIcons.Package,
                        contentDescription = null,
                        modifier = Modifier.size(dimens.iconLg),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(dimens.spacingLg))

                    Text(
                        text = "Pick a pack",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(dimens.spacingXs))

                    Text(
                        text = "Each one bundles what you need to start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(dimens.spacingXl))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 }
            ) {
                Column {
                    ActionToggleGroup(
                        items = SetupPath.entries,
                        selectedItem = selectedPath,
                        onItemSelected = { selectedPath = it },
                        itemLabel = { it.label }
                    )

                    Spacer(Modifier.height(dimens.spacingXl))

                    when (selectedPath) {
                        SetupPath.Packs -> PacksSection(
                            selectedPack = selectedPack,
                            onPackChange = { selectedPack = it },
                            onContinue = { selectedPack?.let(onPackSelected) },
                            enabled = !setupBusy,
                        )

                        SetupPath.Restore -> RestoreSection(
                            onPickBackup = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        )

                        SetupPath.Custom -> CustomSection(
                            onOpenStore = onOpenStore,
                            onPickFile = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 2 }
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSkip)
                        .padding(vertical = dimens.spacingLg)
                )
            }
        }
    }

    pendingImport?.let { (uri, name, size) ->
        ModelImportTypePicker(
            fileName = name,
            onPick = { type ->
                onLocalImport(uri, name, size, type)
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun RestoreSection(
    onPickBackup: () -> Unit,
) {
    val dimens = LocalDimens.current
    Column {
        Text(
            text = "Restore a ToolNeuron backup ZIP with model files, roles, and saved configuration.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(dimens.spacingLg))

        ActionTextButton(
            onClickListener = onPickBackup,
            icon = TnIcons.Download,
            text = "Restore from backup",
        )
    }
}

@Composable
private fun PacksSection(
    selectedPack: String?,
    onPackChange: (String) -> Unit,
    onContinue: () -> Unit,
    enabled: Boolean,
) {
    val dimens = LocalDimens.current
    Column {
        SETUP_PACKS.forEachIndexed { index, pack ->
            PackCard(
                icon = pack.icon,
                title = pack.title,
                subtitle = pack.subtitle,
                selected = selectedPack == pack.id,
                onClick = { onPackChange(pack.id) },
                enabled = enabled,
            )
            if (index != SETUP_PACKS.lastIndex) {
                Spacer(Modifier.height(dimens.spacingSm))
            }
        }

        Spacer(Modifier.height(dimens.spacingXl))

        ActionTextButton(
            onClickListener = onContinue,
            icon = TnIcons.ArrowRight,
            text = "Continue",
            enabled = selectedPack != null && enabled,
        )
    }
}

@Composable
private fun CustomSection(
    onOpenStore: () -> Unit,
    onPickFile: () -> Unit,
) {
    val dimens = LocalDimens.current
    Column {
        Text(
            text = "Browse the full catalog or load a model file you already have.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(dimens.spacingLg))

        ActionTextButton(
            onClickListener = onOpenStore,
            icon = TnIcons.Compass,
            text = "Browse all models",
        )

        Spacer(Modifier.height(dimens.spacingSm))

        ActionTextButton(
            onClickListener = onPickFile,
            icon = TnIcons.FileText,
            text = "Pick a local file",
        )
    }
}

@Composable
private fun PackCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "packBorder"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = Motion.state(),
        label = "packBg"
    )

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconLg)
            )
            Spacer(Modifier.width(dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
