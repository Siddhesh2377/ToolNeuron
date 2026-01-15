package com.dark.tool_neuron.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionModelInfo
import com.dark.tool_neuron.worker.ModelDataParser
import com.dark.tool_neuron.worker.ModelInfo
import com.dark.tool_neuron.worker.ModelLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ModelLoadingActivity : ComponentActivity() {
    private val modelParser = ModelDataParser()
    private var loadedEngine: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NeuroVerseTheme {
                ModelLoadingScreen(
                    modelParser = modelParser,
                    onEngineLoaded = { loadedEngine = it },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        kotlinx.coroutines.MainScope().launch {
            modelParser.unloadModel(loadedEngine)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelLoadingScreen(
    modelParser: ModelDataParser,
    onEngineLoaded: (Any) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var loadingState by remember { mutableStateOf<LoadingState>(LoadingState.Idle) }
    var installState by remember { mutableStateOf<InstallState>(InstallState.NotInstalled) }
    var currentModel by remember { mutableStateOf<Model?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val repository = AppContainer.getModelRepository()
    var isProcessing by remember { mutableStateOf(false) }

    // SAF file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Persist permission for future access
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedUri = uri
        }
    }

    // Animated blur effect
    val infiniteTransition = rememberInfiniteTransition(label = "blur_animation")
    val blurRadius by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blur_radius"
    )

    // Function to open file picker
    fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    // Process selected URI
    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect

        loadingState = LoadingState.Loading
        scope.launch(Dispatchers.IO) {
            isProcessing = true
            try {
                // Get file info from URI
                val modelName = modelParser.getFileNameFromUri(context, uri)
                val fileSize = modelParser.getFileSizeFromUri(context, uri)

                // Calculate hash for deduplication (this reads the entire file)
                val modelHash = modelParser.checksumSHA256FromUri(context, uri)

                val model = Model(
                    id = modelHash,
                    modelPath = uri.toString(),  // Store the content:// URI string
                    modelName = modelName,
                    pathType = PathType.CONTENT_URI,
                    providerType = ProviderType.GGUF,
                    fileSize = fileSize
                )
                currentModel = model

                // Check if already installed
                val existingModel = repository.getModelById(model.id)
                installState = if (existingModel != null) {
                    InstallState.Installed
                } else {
                    InstallState.NotInstalled
                }

                // Load the model using FD
                when (val result = modelParser.loadModelFromUri(context, uri, modelName, null)) {
                    is ModelLoadResult.Success -> {
                        onEngineLoaded(result.engine)
                        loadingState = LoadingState.Loaded(result.info)
                    }

                    is ModelLoadResult.Error -> {
                        loadingState = LoadingState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                loadingState = LoadingState.Error(e.message ?: "Unknown error")
            }
            isProcessing = false
        }
    }

    fun installModel() {
        currentModel?.let { model ->
            scope.launch {
                installState = InstallState.Installing
                try {
                    // Insert model
                    repository.insertModel(model)

                    // Create and insert config based on provider type
                    val config = when (model.providerType) {
                        ProviderType.GGUF -> {
                            val defaultSchema = GgufEngineSchema()
                            ModelConfig(
                                modelId = model.id,
                                modelLoadingParams = defaultSchema.toLoadingJson(),
                                modelInferenceParams = defaultSchema.toInferenceJson()
                            )
                        }

                        ProviderType.DIFFUSION -> {
                            val diffusionConfig = DiffusionConfig()
                            ModelConfig(
                                modelId = model.id,
                                modelLoadingParams = diffusionConfig.toJson(),
                                modelInferenceParams = null
                            )
                        }
                    }

                    repository.insertConfig(config)
                    installState = InstallState.Installed
                } catch (e: Exception) {
                    installState = InstallState.Error(e.message ?: "Installation failed")
                }
            }
        }
    }

    fun uninstallModel() {
        currentModel?.let { model ->
            scope.launch {
                installState = InstallState.Installing
                try {
                    repository.getModelById(model.id)?.let {
                        repository.deleteModel(it)
                    }
                    installState = InstallState.NotInstalled
                } catch (e: Exception) {
                    installState = InstallState.Error(e.message ?: "Uninstall failed")
                }
            }
        }
    }

    // Auto-launch file picker on first load if no model selected
    LaunchedEffect(Unit) {
        if (selectedUri == null && loadingState == LoadingState.Idle) {
            openFilePicker()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Model Loader",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }, actions = {
                    ActionButton(
                        onClickListener = onClose,
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close",
                        shape = RoundedCornerShape(rDp(12.dp))
                    )
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = loadingState,
                modifier = Modifier.then(
                    if (isProcessing) Modifier.blur(radius = blurRadius.dp) else Modifier
                ),
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                }, label = "loading_state"
            ) { state ->
                when (state) {
                    is LoadingState.Idle -> EmptyState { openFilePicker() }
                    is LoadingState.Loading -> LoadingStateView()
                    is LoadingState.Loaded -> ModelInfoView(
                        info = state.info,
                        installState = installState,
                        onChangeModel = { openFilePicker() },
                        onInstall = { installModel() },
                        onUninstall = { uninstallModel() })

                    is LoadingState.Error -> ErrorStateView(state.message) { openFilePicker() }
                }
            }

            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            AnimatedVisibility(
                visible = isProcessing,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(300)
                ),
                exit = fadeOut(animationSpec = tween(300)) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(300)
                )
            ) {
                Column(
                    Modifier
                        .size(rDp(200.dp))
                      ,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LoadingIndicator()
                    Spacer(Modifier.height(rDp(8.dp)))
                    Text("Processing Model....", fontFamily = maple, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPickModel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(24.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = rDp(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(rDp(32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rDp(20.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(rDp(80.dp))
                        .clip(RoundedCornerShape(rDp(20.dp)))
                        .background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ai_model),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(40.dp)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    Text(
                        "No Model Loaded",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Select a model file to begin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                ActionButton(
                    onClickListener = onPickModel,
                    icon = R.drawable.load_model,
                    contentDescription = "Pick Model",
                    shape = RoundedCornerShape(rDp(16.dp)),
                    modifier = Modifier.size(rDp(64.dp))
                )

                Text(
                    "Tap to browse",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadingStateView() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(24.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(rDp(32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rDp(20.dp))
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(rDp(48.dp)), strokeWidth = rDp(4.dp)
                )
                Text(
                    "Loading Model...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "This may take a moment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelInfoView(
    info: ModelInfo,
    installState: InstallState,
    onChangeModel: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    var showDiffusionSettings by remember { mutableStateOf(false) }
    var diffusionConfig by remember {
        mutableStateOf(
            if (info is DiffusionModelInfo) info.modelConfig else DiffusionConfig()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Header Card with Model Icon & Info
        Surface(
            shape = RoundedCornerShape(rDp(20.dp)),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = rDp(1.dp)
        ) {
            Column(modifier = Modifier.padding(rDp(20.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDp(16.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .size(rDp(56.dp))
                            .clip(RoundedCornerShape(rDp(14.dp)))
                            .background(MaterialTheme.colorScheme.primary.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(
                                when (info.providerType) {
                                    ProviderType.DIFFUSION -> R.drawable.load_model // Add image icon
                                    else -> R.drawable.ai_model
                                }
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(rDp(32.dp)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            info.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                info.architecture,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                            )

                            // Model type badge
                            Surface(
                                shape = RoundedCornerShape(rDp(4.dp)),
                                color = MaterialTheme.colorScheme.primary.copy(0.2f)
                            ) {
                                Text(
                                    text = when (info.providerType) {
                                        ProviderType.GGUF -> "TEXT"
                                        ProviderType.DIFFUSION -> "IMAGE"
                                        else -> "OTHER"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = rDp(6.dp), vertical = rDp(2.dp)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    ActionButton(
                        onClickListener = onChangeModel,
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "Change Model",
                        shape = RoundedCornerShape(rDp(12.dp))
                    )
                }

                // Installation Status Badge
                Spacer(modifier = Modifier.height(rDp(16.dp)))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.1f))
                Spacer(modifier = Modifier.height(rDp(16.dp)))

                AnimatedContent(
                    targetState = installState, label = "install_state", transitionSpec = {
                        (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut())
                    }) { state ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (state) {
                                InstallState.Installed -> {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(rDp(24.dp))
                                    )
                                    Column {
                                        Text(
                                            "Installed",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Ready to use",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                0.6f
                                            )
                                        )
                                    }
                                }

                                InstallState.Installing -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(rDp(24.dp)),
                                        strokeWidth = rDp(2.dp)
                                    )
                                    Text(
                                        "Processing...",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                InstallState.NotInstalled -> {
                                    Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                            0.6f
                                        ),
                                        modifier = Modifier.size(rDp(24.dp))
                                    )
                                    Column {
                                        Text(
                                            "Not Installed",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Add to database",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                0.6f
                                            )
                                        )
                                    }
                                }

                                is InstallState.Error -> {
                                    Text(
                                        "Error: ${state.message}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Action Buttons Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            // Settings button for Diffusion models
                            if (info is DiffusionModelInfo && state == InstallState.Installed) {
                                ActionButton(
                                    onClickListener = { showDiffusionSettings = true },
                                    icon = Icons.Outlined.Settings,
                                    contentDescription = "Configure",
                                    shape = RoundedCornerShape(rDp(12.dp))
                                )
                            }

                            // Install/Uninstall button
                            if (state != InstallState.Installing) {
                                ActionButton(
                                    onClickListener = {
                                        when (state) {
                                            InstallState.NotInstalled -> onInstall()
                                            InstallState.Installed -> onUninstall()
                                            else -> {}
                                        }
                                    }, icon = when (state) {
                                        InstallState.NotInstalled -> Icons.Outlined.Download
                                        InstallState.Installed -> Icons.Outlined.Delete
                                        else -> Icons.Outlined.Download
                                    }, contentDescription = when (state) {
                                        InstallState.NotInstalled -> "Install"
                                        InstallState.Installed -> "Uninstall"
                                        else -> "Action"
                                    }, shape = RoundedCornerShape(rDp(12.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Description Section
        if (info.description.isNotEmpty()) {
            InfoSection(title = "Description") {
                InfoCard {
                    Text(
                        info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Parameters Section
        if (info.parameters.isNotEmpty()) {
            InfoSection(
                title = when (info.providerType) {
                    ProviderType.DIFFUSION -> "Model Configuration"
                    else -> "Model Parameters"
                }
            ) {
                InfoCard {
                    info.parameters.entries.forEachIndexed { index, (key, value) ->
                        InfoRow(key, value)
                        if (index < info.parameters.size - 1) {
                            Spacer(modifier = Modifier.height(rDp(8.dp)))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
                                thickness = 1.dp
                            )
                            Spacer(modifier = Modifier.height(rDp(8.dp)))
                        }
                    }
                }
            }
        }

        // Additional Info Section (Vocabulary for GGUF, Model Info for Diffusion)
        info.additionalInfo?.let { additionalData ->
            if (additionalData.isNotEmpty()) {
                InfoSection(
                    title = when (info.providerType) {
                        ProviderType.DIFFUSION -> "Additional Info"
                        else -> "Vocabulary Info"
                    }
                ) {
                    InfoCard {
                        additionalData.entries.forEachIndexed { index, (key, value) ->
                            InfoRow(key, value)
                            if (index < additionalData.size - 1) {
                                Spacer(modifier = Modifier.height(rDp(8.dp)))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
                                    thickness = 1.dp
                                )
                                Spacer(modifier = Modifier.height(rDp(8.dp)))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(rDp(16.dp)))
    }

    // Diffusion Settings Dialog
    if (showDiffusionSettings && info is DiffusionModelInfo) {
        DiffusionConfigDialog(
            config = diffusionConfig,
            onDismiss = { showDiffusionSettings = false },
            onSave = { newConfig ->
                diffusionConfig = newConfig
                // TODO: Save to database
                showDiffusionSettings = false
            })
    }
}

@Composable
private fun DiffusionConfigDialog(
    config: DiffusionConfig, onDismiss: () -> Unit, onSave: (DiffusionConfig) -> Unit
) {
    var runOnCpu by remember { mutableStateOf(config.runOnCpu) }
    var useCpuClip by remember { mutableStateOf(config.useCpuClip) }
    var safetyMode by remember { mutableStateOf(config.safetyMode) }
    var isPony by remember { mutableStateOf(config.isPony) }
    var textEmbeddingSize by remember { mutableStateOf(config.textEmbeddingSize) }

    AlertDialog(
        onDismissRequest = onDismiss, title = {
        Text(
            "Diffusion Model Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }, text = {
        Column(
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp)),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            // Backend Section
            Text(
                "Backend Configuration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                label = "Run on CPU",
                description = "Use CPU instead of NPU/GPU",
                checked = runOnCpu,
                onCheckedChange = { runOnCpu = it })

            SettingRow(
                label = "CPU CLIP",
                description = "Use CPU for CLIP model",
                checked = useCpuClip,
                onCheckedChange = { useCpuClip = it })

            HorizontalDivider()

            // Model Variant Section
            Text(
                "Model Variant",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                label = "Pony Diffusion",
                description = "Enable Pony v6 specific optimizations",
                checked = isPony,
                onCheckedChange = { isPony = it })

            HorizontalDivider()

            // Safety Section
            Text(
                "Safety & Filtering",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                label = "Safety Filter",
                description = "Enable content safety checker",
                checked = safetyMode,
                onCheckedChange = { safetyMode = it })

            HorizontalDivider()

            // Text Embedding Size
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Text Embedding Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Current: $textEmbeddingSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                        FilterChip(
                            selected = textEmbeddingSize == 768,
                            onClick = { textEmbeddingSize = 768 },
                            label = { Text("768") })
                        FilterChip(
                            selected = textEmbeddingSize == 1024,
                            onClick = { textEmbeddingSize = 1024 },
                            label = { Text("1024") })
                    }
                }
            }
        }
    }, confirmButton = {
        Button(
            onClick = {
                onSave(
                    config.copy(
                        runOnCpu = runOnCpu,
                        useCpuClip = useCpuClip,
                        safetyMode = safetyMode,
                        isPony = isPony,
                        textEmbeddingSize = textEmbeddingSize
                    )
                )
            }) {
            Text("Save Changes")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }, shape = RoundedCornerShape(rDp(20.dp))
    )
}

@Composable
private fun SettingRow(
    label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked, onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ErrorStateView(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(24.dp)),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(
                modifier = Modifier.padding(rDp(32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
            ) {
                Text(
                    "Error Loading Model",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f),
                    textAlign = TextAlign.Center
                )

                ActionButton(
                    onClickListener = onRetry,
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Try Another Model",
                    shape = RoundedCornerShape(rDp(12.dp))
                )
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String, content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = rDp(4.dp))
        )
        content()
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(rDp(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f)
        )
        Spacer(Modifier.width(rDp(16.dp)))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

sealed class LoadingState {
    data object Idle : LoadingState()
    data object Loading : LoadingState()
    data class Loaded(val info: ModelInfo) : LoadingState()
    data class Error(val message: String) : LoadingState()
}

sealed class InstallState {
    data object NotInstalled : InstallState()
    data object Installing : InstallState()
    data object Installed : InstallState()
    data class Error(val message: String) : InstallState()
}