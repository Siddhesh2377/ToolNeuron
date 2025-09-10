package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.data.ModelsList.getModelList
import com.dark.ai_module.model.ModelsData
import com.dark.neuroverse.activity.ModelLoadingActivity
import com.dark.neuroverse.ui.components.CollapsableButton
import com.dark.neuroverse.ui.components.ModelDialog
import com.dark.neuroverse.ui.components.StandardBottomBar
import com.dark.neuroverse.ui.theme.Success
import com.dark.neuroverse.ui.theme.onSuccess
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.DownloadState
import com.dark.neuroverse.viewModel.ModelScreenViewModel
import com.dark.neuroverse.viewModel.ModelScreenViewModelFactory

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    val factory = remember { ModelScreenViewModelFactory(context) }
    val viewModel: ModelScreenViewModel = viewModel(factory = factory)

    // Marketplace data (remote catalog)
    val marketplace = remember { getModelList(context) }

    // Installed state from VM
    val installedModels by viewModel.models.collectAsState()
    var isEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(installedModels) { isEnabled = installedModels.isNotEmpty() }

    // (Kept for future) File picker for local .gguf imports
    var showDialog by remember { mutableStateOf(false) }
    var selectedModelPath by remember { mutableStateOf<java.io.File?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.loadModelDetailsFromFile(it, context) { file ->
                selectedModelPath = file
                showDialog = true
            }
        }
    }

    // Tabs: Marketplace | Installed LLM
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("MarketPlace", "Installed LLM")

    Scaffold { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Header + Import button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Choose Your\nModels",
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                    )
                )

                Button(onClick = {
                    // Launch dedicated import screen
                    context.startActivity(Intent(context, ModelLoadingActivity::class.java))
                }) {
                    Icon(Icons.TwoTone.FileOpen, contentDescription = "Import Model")
                    Spacer(Modifier.width(12.dp))
                    Text("Import")
                }
            }

            // Optional legacy local import dialog (still supported if you call filePickerLauncher elsewhere)
            if (showDialog) {
                ModelDialog(
                    modelInfo = selectedModelPath ?: java.io.File("default_model.gguf"),
                    onDismiss = { showDialog = false },
                    onSave = { vmModel ->
                        viewModel.loadModel(vmModel)
                        showDialog = false
                    }
                )
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            // Animated content per tab
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> MarketplaceList(viewModel)
                    else -> InstalledList(viewModel)
                }
            }

            StandardBottomBar(Modifier.padding(bottom = 14.dp)) {
                CollapsableButton(
                    text = "Finish",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = isEnabled
                ) { onNext() }
            }
        }
    }
}

// ——————————————————————————————————————————————————————————
// Marketplace Tab
// ——————————————————————————————————————————————————————————
@Composable
private fun MarketplaceList(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val models = remember { getModelList(context) }
    val downloadStates by viewModel.downloadStates.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        items(models) { modelData ->
            val state: DownloadState = downloadStates[modelData.modeName] ?: DownloadState()
            ModelCard(
                modelsData = modelData,
                isDownloading = state.isDownloading,
                progress = state.progress,
                onDownloadComplete = state.isComplete,
                viewModel = viewModel,
                onDownload = { viewModel.startDownload(modelData) }
            )
        }
    }
}

// ——————————————————————————————————————————————————————————
// Installed LLM Tab
// ——————————————————————————————————————————————————————————
@Composable
private fun InstalledList(viewModel: ModelScreenViewModel) {
    val installed by viewModel.models.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        items(installed, key = { it.modeName }) { model ->
            InstalledModelCard(
                model = model,
                onDelete = { viewModel.removeModel(model.modeName) }
            )
        }
    }
}

// ——————————————————————————————————————————————————————————
// Installed card — compact, readable specs
// ——————————————————————————————————————————————————————————
@Composable
private fun InstalledModelCard(
    model: ModelsData,
    onDelete: () -> Unit
) {
    val isLocalImport = model.modelLink.isBlank() && model.modelPageLink.isBlank()
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            model.modeName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Pill(if (isLocalImport) "Local" else "Remote")
                    }
                    if (!isLocalImport && model.modelDescription.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            model.modelDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.TwoTone.Delete, contentDescription = "Delete")
                }
            }

            // Specs grid
            SpecGrid(
                "Size" to ("${model.modelSize} MB"),
                "Context" to model.modelCtxSize.toString(),
                "Tools" to model.toolUse.uppercase(),
                "Path" to model.modelPath,
            )

            // External page for remote models
            if (!isLocalImport && model.modelPageLink.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val ctx = LocalContext.current
                    IconButton(onClick = {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, model.modelPageLink.toUri())) } catch (_: Exception) {}
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
                }
            }
        }
    }
}

// ——————————————————————————————————————————————————————————
// Marketplace card — optimized info layout
// ——————————————————————————————————————————————————————————
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelCard(
    modelsData: ModelsData,
    isDownloading: Boolean = false,
    onDownloadComplete: Boolean = false,
    progress: Float = 0f,
    viewModel: ModelScreenViewModel,
    onDownload: () -> Unit = {}
) {
    val context = LocalContext.current
    var isInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.checkIfInstalled(modelsData.modeName) { isInstalled = it } }
    LaunchedEffect(onDownloadComplete) { if (onDownloadComplete) isInstalled = true }

    val buttonColor = if (!isInstalled) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
        containerColor = onSuccess,
        contentColor = Success
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                modelsData.modeName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (modelsData.modelDescription.isNotBlank()) {
                Text(modelsData.modelDescription, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            // Specs as chips + grid
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("${modelsData.modelSize} MB")
                Spacer(Modifier.width(8.dp))
                Pill("Ctx ${modelsData.modelCtxSize}")
                Spacer(Modifier.width(8.dp))
                Pill(modelsData.toolUse.uppercase())
            }

            SpecGrid(
                "Context" to modelsData.modelCtxSize.toString(),
                "Size" to ("${modelsData.modelSize} MB"),
                "Tools" to modelsData.toolUse.uppercase(),
                "Path" to modelsData.modelPath
            )

            AnimatedVisibility(visible = isDownloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { progress })
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (!isInstalled) {
                            if (isDownloading) viewModel.cancelDownload(modelsData.modeName, modelsData.modelPath) else onDownload()
                        }
                    },
                    colors = buttonColor
                ) {
                    AnimatedContent(targetState = isInstalled, transitionSpec = { fadeIn() togetherWith fadeOut() }) { installed ->
                        if (installed) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            AnimatedContent(targetState = isDownloading, transitionSpec = { fadeIn() togetherWith fadeOut() }) { downloading ->
                                if (downloading) Icon(Icons.Filled.Stop, contentDescription = null) else Icon(Icons.Filled.ArrowCircleDown, contentDescription = null)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(rDP(8.dp)))

                AnimatedVisibility(visible = isInstalled) {
                    Button(
                        onClick = {
                            viewModel.removeModel(modelsData.modeName)
                            isInstalled = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Icon(Icons.TwoTone.Delete, contentDescription = null) }
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, modelsData.modelPageLink.toUri())
                    context.startActivity(intent)
                }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
            }
        }
    }
}

// ——————————————————————————————————————————————————————————
// Reusable UI bits
// ——————————————————————————————————————————————————————————
@Composable
private fun Pill(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(vertical = 2.dp, horizontal = 0.dp)
            .padding(end = 0.dp),
        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

@Composable
private fun SpecGrid(vararg pairs: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pairs.forEach { (k, v) -> SpecRow(k, v) }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
