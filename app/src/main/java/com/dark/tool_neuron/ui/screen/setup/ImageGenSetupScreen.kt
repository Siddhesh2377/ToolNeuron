package com.dark.tool_neuron.ui.screen.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import com.dark.tool_neuron.ui.theme.Motion
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.ai_sd.DiffusionRuntimeConfig
import com.dark.ai_sd.RuntimeSetupState
import com.dark.ai_sd.StableDiffusionManager
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

// ── Constants ──

private const val QNN_LIBS_URL = "https://huggingface.co/Void2377/QNN-LIBS/resolve/main/qnnlibs.tar.xz"
private const val SAFETY_CHECKER_URL = "https://huggingface.co/Void2377/QNN-LIBS/resolve/main/safety_checker.mnn"
private const val RUNTIME_DIR = "runtime_libs/qnnlibs"

// ── Sealed state for this screen ──

private sealed class SetupPhase {
    object Checking : SetupPhase()
    object AlreadyDone : SetupPhase()
    object Ready : SetupPhase()
    object NoInternet : SetupPhase()
    data class Downloading(
        val fileName: String,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : SetupPhase()
    object Extracting : SetupPhase()
    object Complete : SetupPhase()
    data class Error(val message: String) : SetupPhase()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageGenSetupScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf<SetupPhase>(SetupPhase.Checking) }
    var runtimeState by remember { mutableStateOf<RuntimeSetupState>(RuntimeSetupState.Idle) }

    // Check if already extracted on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val runtimeDir = File(context.filesDir, RUNTIME_DIR)
            val marker = File(runtimeDir, ".extracted")
            if (marker.exists() && (runtimeDir.listFiles()?.size ?: 0) > 1) {
                phase = SetupPhase.AlreadyDone
            } else {
                phase = SetupPhase.Ready
            }
        }
    }

    // Auto-complete if already done
    LaunchedEffect(phase) {
        if (phase is SetupPhase.AlreadyDone) {
            onComplete()
        }
    }

    fun startSetup() {
        scope.launch(Dispatchers.IO) {
            try {
                // Step 1: Check internet connectivity
                val hasInternet = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("huggingface.co", 443), 5000)
                        true
                    }
                } catch (_: Exception) {
                    false
                }

                if (!hasInternet) {
                    phase = SetupPhase.NoInternet
                    return@launch
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val cacheDir = context.cacheDir

                // Step 2: Download qnnlibs.tar.xz
                val tarXzFile = File(cacheDir, "qnnlibs.tar.xz")
                downloadFile(client, QNN_LIBS_URL, tarXzFile, "QNN Libraries") { downloaded, total ->
                    phase = SetupPhase.Downloading("QNN Libraries", downloaded, total)
                }

                // Step 3: Download safety_checker.mnn
                val safetyFile = File(cacheDir, "safety_checker.mnn")
                downloadFile(client, SAFETY_CHECKER_URL, safetyFile, "Safety Checker") { downloaded, total ->
                    phase = SetupPhase.Downloading("Safety Checker", downloaded, total)
                }

                // Step 4: Extract and initialize via DiffusionManager
                phase = SetupPhase.Extracting

                val sdManager = StableDiffusionManager.getInstance(context)

                // Observe state from the manager
                launch {
                    sdManager.runtimeSetupState.collect { state ->
                        runtimeState = state
                        when (state) {
                            is RuntimeSetupState.Complete -> phase = SetupPhase.Complete
                            is RuntimeSetupState.Error -> phase = SetupPhase.Error(state.message)
                            else -> {}
                        }
                    }
                }

                sdManager.initialize(
                    DiffusionRuntimeConfig(
                        runtimeDir = RUNTIME_DIR,
                        safetyCheckerEnabled = true,
                        tarXzSourcePath = tarXzFile.absolutePath,
                        safetyCheckerSourcePath = safetyFile.absolutePath
                    )
                )
            } catch (e: Exception) {
                phase = SetupPhase.Error(e.message ?: "Setup failed")
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Image Generation Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back"
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Standards.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(Motion.entrance()) + expandVertically(Motion.entrance())) togetherWith
                            (fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()))
                },
                label = "phase"
            ) { currentPhase ->
                when (currentPhase) {
                    is SetupPhase.Checking -> CheckingContent()
                    is SetupPhase.AlreadyDone -> {} // Auto-navigates
                    is SetupPhase.Ready -> ReadyContent(
                        onStart = { startSetup() },
                        onSkip = onSkip
                    )
                    is SetupPhase.NoInternet -> NoInternetContent(
                        onRetry = { startSetup() },
                        onSkip = onSkip
                    )
                    is SetupPhase.Downloading -> DownloadingContent(
                        fileName = currentPhase.fileName,
                        bytesDownloaded = currentPhase.bytesDownloaded,
                        totalBytes = currentPhase.totalBytes
                    )
                    is SetupPhase.Extracting -> ExtractingContent(runtimeState)
                    is SetupPhase.Complete -> CompleteContent(onContinue = onComplete)
                    is SetupPhase.Error -> ErrorContent(
                        message = currentPhase.message,
                        onRetry = { startSetup() },
                        onSkip = onSkip
                    )
                }
            }
        }
    }
}

// ── Download helper ──

private fun downloadFile(
    client: OkHttpClient,
    url: String,
    target: File,
    label: String,
    onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
) {
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()

    if (!response.isSuccessful) {
        throw RuntimeException("Failed to download $label: HTTP ${response.code}")
    }

    val body = response.body
    val totalBytes = body.contentLength()

    body.byteStream().use { input ->
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(64 * 1024)
            var downloaded = 0L
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } >= 0) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                onProgress(downloaded, totalBytes)
            }
        }
    }
}

// ── Sub-composables ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Text(
            "Checking setup status...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadyContent(
    onStart: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TnIcons.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Image Generation Runtime",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                "ToolNeuron needs to download QNN libraries and the safety checker to enable on-device image generation. This is a one-time setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Standards.SpacingSm))

            InfoRow(label = "QNN Runtime", value = "~29 MB download")
            InfoRow(label = "Safety Checker", value = "~12 MB download")
            InfoRow(label = "Internet", value = "Required (HuggingFace)")

            Spacer(Modifier.height(Standards.SpacingSm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionTextButton(
                    onClickListener = onSkip,
                    icon = TnIcons.X,
                    text = "Skip",
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    modifier = Modifier.weight(1f)
                )
                ActionTextButton(
                    onClickListener = onStart,
                    icon = TnIcons.Download,
                    text = "Download",
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NoInternetContent(
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Icon(
                TnIcons.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                "No Internet Connection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                "An internet connection is required to download the image generation runtime (~40 MB). Please check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Standards.SpacingSm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionTextButton(
                    onClickListener = onSkip,
                    icon = TnIcons.X,
                    text = "Skip",
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    modifier = Modifier.weight(1f)
                )
                ActionTextButton(
                    onClickListener = onRetry,
                    icon = TnIcons.Refresh,
                    text = "Retry",
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadingContent(
    fileName: String,
    bytesDownloaded: Long,
    totalBytes: Long
) {
    val progress = if (totalBytes > 0) {
        (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
    } else -1f

    val animatedProgress by animateFloatAsState(
        targetValue = if (progress >= 0f) progress else 0f,
        animationSpec = Motion.content(),
        label = "dlProg"
    )

    val downloadedMb = "%.1f".format(bytesDownloaded / (1024f * 1024f))
    val totalMb = if (totalBytes > 0) "%.1f".format(totalBytes / (1024f * 1024f)) else "?"

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            LoadingIndicator(modifier = Modifier.size(48.dp))

            Text(
                "Downloading...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            Text(
                "$downloadedMb / $totalMb MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Text(
                "Do not close the app",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtractingContent(state: RuntimeSetupState) {
    val statusText = when (state) {
        is RuntimeSetupState.CopyingAsset -> "Preparing archive..."
        is RuntimeSetupState.Extracting -> "Extracting: ${state.currentFile}"
        is RuntimeSetupState.CopyingSafetyChecker -> "Setting up safety checker..."
        is RuntimeSetupState.InitializingRuntime -> "Initializing QNN runtime..."
        else -> "Processing..."
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            LoadingIndicator(modifier = Modifier.size(48.dp))

            Text(
                "Setting up...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Text(
                "Do not close the app",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CompleteContent(onContinue: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TnIcons.CircleCheck,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Setup Complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "QNN libraries and safety checker are ready. You can now load image generation models.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Standards.SpacingSm))

            ActionTextButton(
                onClickListener = onContinue,
                icon = TnIcons.ChevronRight,
                text = "Continue",
                shape = RoundedCornerShape(Standards.RadiusLg)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Standards.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Icon(
                TnIcons.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                "Setup Failed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Standards.SpacingSm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionTextButton(
                    onClickListener = onSkip,
                    icon = TnIcons.X,
                    text = "Skip",
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    modifier = Modifier.weight(1f)
                )
                ActionTextButton(
                    onClickListener = onRetry,
                    icon = TnIcons.Refresh,
                    text = "Retry",
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Helper composables ──

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(Standards.SpacingXs),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingXxs)
            )
        }
    }
}
