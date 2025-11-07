package com.dark.neuroverse.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.components.FuturisticNeuralAnimation
import com.dark.neuroverse.ui.components.NeuralThemes
import com.dark.neuroverse.ui.theme.CyberViolet
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.stt.STTEvent
import com.dark.neuroverse.viewModel.stt.STTViewModel
import com.dark.neuroverse.worker.DataHubManager
import com.dark.plugins.model.Tools
import com.mp.data_hub_lib.model.DataSetModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputWithDataHubDialog(
    value: String,
    tools: List<Pair<String, List<Tools>>>,
    selectedTools: List<Tools>,
    onToolSelected: (Pair<String, Tools>) -> Unit,
    onValueChange: (String) -> Unit,
    onRag: (Boolean) -> Unit,
    onSend: () -> Unit,
    onToolRemoved: (Tools) -> Unit,
    isGenerating: Boolean,
    inputEnabled: Boolean,
    sttViewModel: STTViewModel = viewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentDataset by DataHubManager.currentDataSet.collectAsState()
    val datasets by DataHubManager.installedDataSets.collectAsState()

    LaunchedEffect(Unit) {
        val modelDir = ModelManager.getSTTModel() ?: return@LaunchedEffect
        launch(Dispatchers.IO) {
            sttViewModel.initialize(
                modelDir
            )
        }
    }

    ChatInputBar(
        value = value,
        tools = tools,
        selectedTools = selectedTools,
        onToolSelected = onToolSelected,
        onValueChange = onValueChange,
        onRag = { ragEnabled ->
            onRag(ragEnabled)
            if (ragEnabled) showDialog = true
        },
        onSend = onSend,
        onToolRemoved = onToolRemoved,
        isGenerating = isGenerating,
        inputEnabled = inputEnabled,
        sttViewModel = sttViewModel
    )


    if (showDialog) {
        DatasetSelectionDialog(
            datasets = datasets,
            currentDataset = currentDataset,
            onDismiss = { showDialog = false })
    }
}

@Composable
private fun DatasetSelectionDialog(
    datasets: List<DataSetModel>, currentDataset: DataSetModel?, onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Dataset") }, text = {
        if (datasets.isEmpty()) {
            Text("No datasets installed", color = Color.Gray)
        } else {
            LazyColumn {
                items(datasets) { dataset ->
                    DatasetItem(
                        dataset = dataset,
                        isSelected = currentDataset?.modelName == dataset.modelName
                    )
                }
            }
        }
    }, confirmButton = {}, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    })
}

@Composable
private fun DatasetItem(dataset: DataSetModel, isSelected: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                if (isSelected) {
                    DataHubManager.clearCurrentDataSet()
                } else {
                    DataHubManager.setCurrentDataSet(dataset) { success ->
                        if (!success) Log.e("DataHub", "Failed to set dataset")
                    }
                }
            }, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(dataset.modelName, style = MaterialTheme.typography.bodyLarge)
                if (dataset.modelDescription.isNotBlank()) {
                    Text(
                        dataset.modelDescription,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ChatInputBar(
    value: String,
    tools: List<Pair<String, List<Tools>>>,
    selectedTools: List<Tools>,
    onToolSelected: (Pair<String, Tools>) -> Unit,
    onValueChange: (String) -> Unit,
    onRag: (Boolean) -> Unit,
    onSend: () -> Unit,
    onToolRemoved: (Tools) -> Unit,
    isGenerating: Boolean,
    inputEnabled: Boolean,
    sttViewModel: STTViewModel
) {
    var showToolsList by remember { mutableStateOf(false) }
    var isRag by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val stopRecordingFlag = remember { AtomicBoolean(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sttUiState by sttViewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val message =
            if (granted) "Microphone permission granted" else "Microphone permission denied"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Handle STT events
    LaunchedEffect(Unit) {
        sttViewModel.events.collect { event ->
            handleSTTEvent(event, context, value, onValueChange)
        }
    }

    Column(
        modifier = Modifier
            .imePadding()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondary.copy(0.1f)),
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        Column(Modifier.navigationBarsPadding()) {
            AnimatedVisibility(visible = showToolsList) {
                ToolsList(
                    tools = tools, onToolSelected = {
                        onToolSelected(it)
                        showToolsList = false
                    })
            }

            ToolsAndModelRow(
                inputEnabled = inputEnabled,
                isToolCalling = ModelManager.currentModel.collectAsState().value.isToolCalling,
                showToolsList = showToolsList,
                selectedTools = selectedTools,
                isRag = isRag,
                onToolsClick = { if (inputEnabled) showToolsList = !showToolsList },
                onToolRemoved = onToolRemoved,
                onRagToggle = {
                    if (inputEnabled) {
                        isRag = !isRag
                        onRag(isRag)
                    }
                })

            InputRow(
                value = value,
                onValueChange = onValueChange,
                inputEnabled = inputEnabled,
                isRecording = isRecording,
                isTranscribing = sttUiState.isTranscribing,
                isGenerating = isGenerating,
                sttReady = sttUiState.isReady,
                onSTTClick = {
                    handleSTTButtonClick(
                        context = context,
                        sttReady = sttUiState.isReady,
                        isRecording = isRecording,
                        stopRecordingFlag = stopRecordingFlag,
                        permissionLauncher = permissionLauncher,
                        onRecordingStateChange = { isRecording = it },
                        sttViewModel = sttViewModel,
                        scope = scope
                    )
                },
                onSend = {
                    if (ModelManager.isModelLoaded()) {
                        onSend()
                    } else {
                        Toast.makeText(
                            context,
                            "Model is not loaded..! \nPlease Load Model..!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
        }
    }
}

private fun handleSTTButtonClick(
    context: Context,
    sttReady: Boolean,
    isRecording: Boolean,
    stopRecordingFlag: AtomicBoolean,
    permissionLauncher: ActivityResultLauncher<String>,
    onRecordingStateChange: (Boolean) -> Unit,
    sttViewModel: STTViewModel,
    scope: CoroutineScope
) {
    if (!sttReady) {
        Toast.makeText(context, "STT not initialized", Toast.LENGTH_SHORT).show()
        return
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        return
    }

    if (isRecording) {
        // Signal to stop recording
        stopRecordingFlag.set(true)
        onRecordingStateChange(false)
    } else {
        // Start recording
        stopRecordingFlag.set(false)
        onRecordingStateChange(true)

        scope.launch(Dispatchers.IO) {
            try {
                val audioFile = recordAudioUntilStopped(context) { stopRecordingFlag.get() }

                // Check if we have valid audio
                if (audioFile.exists() && audioFile.length() > 1000) {
                    withContext(Dispatchers.Main) {
                        Log.d("ChatInputBar", "Starting transcription: ${audioFile.absolutePath}")
                        sttViewModel.transcribeFile(audioFile.absolutePath)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Recording too short or empty", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } catch (_: CancellationException) {
                Log.d("ChatInputBar", "Recording cancelled")
            } catch (e: Exception) {
                Log.e("ChatInputBar", "Recording error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Recording error: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onRecordingStateChange(false)
                }
            }
        }
    }
}

private fun handleSTTEvent(
    event: STTEvent, context: Context, currentValue: String, onValueChange: (String) -> Unit
) {
    when (event) {
        is STTEvent.TranscriptionSuccess -> {
            val newText = if (currentValue.isBlank()) event.text else "$currentValue ${event.text}"
            onValueChange(newText)
            Toast.makeText(context, "Transcribed! ${event.text}", Toast.LENGTH_SHORT).show()
        }

        is STTEvent.TranscriptionFailed -> {
            Toast.makeText(context, "Transcription failed: ${event.message}", Toast.LENGTH_SHORT)
                .show()
        }

        is STTEvent.NoSpeechDetected -> {
            Toast.makeText(context, "No speech detected", Toast.LENGTH_SHORT).show()
        }

        is STTEvent.Error -> {
            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
        }

        else -> {}
    }
}

@Composable
private fun ToolsAndModelRow(
    inputEnabled: Boolean,
    isToolCalling: Boolean,
    showToolsList: Boolean,
    selectedTools: List<Tools>,
    isRag: Boolean,
    onToolsClick: () -> Unit,
    onToolRemoved: (Tools) -> Unit,
    onRagToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(top = rDP(16.dp))
            .padding(horizontal = rDP(16.dp))
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        Button(
            onClick = onToolsClick, enabled = isToolCalling, colors = ButtonDefaults.buttonColors(
                containerColor = if (showToolsList) SkyBlue else MaterialTheme.colorScheme.background,
                contentColor = if (showToolsList) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
            ), shape = RoundedCornerShape(rDP(8.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                Icon(painterResource(R.drawable.tools), contentDescription = "Tools")
                Text(text = "Tools", fontSize = rSp(14.sp))
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(rDP(4.dp))
        ) {
            items(selectedTools, key = { it.toolName }) { tool ->
                ToolChip(
                    tool = tool,
                    onRemove = { onToolRemoved(tool) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        IconButton(
            onClick = onRagToggle,
            enabled = inputEnabled,
            modifier = Modifier.size(rDP(36.dp)),
            shape = RoundedCornerShape(rDP(8.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isRag) CyberViolet.copy(0.2f) else MaterialTheme.colorScheme.background,
                contentColor = if (isRag) CyberViolet else MaterialTheme.colorScheme.primary,
            )
        ) {
            Icon(painterResource(R.drawable.database_zap), contentDescription = "Toggle RAG")
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    inputEnabled: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isGenerating: Boolean,
    sttReady: Boolean,
    onSTTClick: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = rDP(200.dp))
            .padding(vertical = rDP(8.dp))
            .padding(bottom = rDP(4.dp))
            .padding(end = rDP(18.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {


        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = inputEnabled && !isRecording,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = rDP(6.dp)),
            placeholder = {
                Text(
                    text = when {
                        isRecording -> "Recording..."
                        isTranscribing -> "Transcribing..."
                        inputEnabled -> "Say Anything…"
                        else -> "Processing..."
                    }, color = SlateGrey, fontSize = rSp(14.sp)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.primary, fontSize = rSp(15.sp)
            )
        )

        Spacer(Modifier.width(rDP(4.dp)))


        STTButton(
            isRecording = isRecording,
            isProcessing = isTranscribing,
            isReady = sttReady,
            onClick = onSTTClick
        )

        Spacer(Modifier.width(rDP(8.dp)))

        SendButton(
            inputEnabled = inputEnabled, isGenerating = isGenerating, onSend = onSend
        )
    }
}

@Composable
private fun SendButton(
    inputEnabled: Boolean, isGenerating: Boolean, onSend: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(rDP(36.dp))
            .clip(CircleShape)
            .background(
                if (inputEnabled || isGenerating) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            .clickable(enabled = inputEnabled || isGenerating) {
                onSend()
            }, contentAlignment = Alignment.Center
    ) {
        when {
            isGenerating -> {
                Icon(
                    Icons.Rounded.Stop,
                    modifier = Modifier.padding(rDP(8.dp)),
                    contentDescription = "Stop",
                    tint = MaterialTheme.colorScheme.background
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(rDP(28.dp)),
                    trackColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                    color = MaterialTheme.colorScheme.background
                )
            }

            else -> {
                Icon(
                    painterResource(R.drawable.send_chat),
                    modifier = Modifier.padding(rDP(8.dp)),
                    contentDescription = "Send",
                    tint = if (inputEnabled) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
private fun ToolChip(
    tool: Tools, onRemove: () -> Unit, modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0066FF)
    val backgroundColor = accentColor.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .size(ButtonDefaults.MinHeight)
            .background(color = backgroundColor, shape = RoundedCornerShape(rDP(8.dp)))
            .clickable { onRemove() }, contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Web, contentDescription = "Remove ${tool.toolName}", tint = accentColor
        )
    }
}

@Composable
fun ToolsList(
    modifier: Modifier = Modifier,
    tools: List<Pair<String, List<Tools>>>,
    onToolSelected: (Pair<String, Tools>) -> Unit
) {
    LazyColumn(
        modifier = modifier.heightIn(min = rDP(100.dp), max = rDP(300.dp)),
        contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        tools.forEach { (pluginName, toolList) ->
            item {
                Text(
                    text = pluginName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = rSp(18.sp)),
                    modifier = Modifier.padding(horizontal = rDP(16.dp), vertical = rDP(8.dp))
                )
            }

            items(toolList) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rDP(32.dp), vertical = rDP(4.dp))
                        .clickable { onToolSelected(Pair(pluginName, tool)) },
                    elevation = CardDefaults.cardElevation(rDP(0.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(modifier = Modifier.padding(rDP(12.dp))) {
                        Text(
                            text = tool.toolName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = rSp(16.sp))
                        )
                        if (tool.description.isNotBlank()) {
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = rSp(13.sp)),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}