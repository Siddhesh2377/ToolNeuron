package com.dark.tool_neuron.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewModel.setupScreen.DownloadStatus
import com.dark.tool_neuron.viewModel.setupScreen.ModelDownloadState
import com.dark.tool_neuron.viewModel.setupScreen.SetupViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.exitProcess

class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ModelManager.init(applicationContext)

        setContent {
            NeuroVerseTheme {
                val state by viewModel.state.collectAsState()
                var showDownloadScreen by remember { mutableStateOf(false) }
                var showRestartDialog by remember { mutableStateOf(false) }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface, topBar = {
                        AnimatedTopBar(showDownloadScreen = showDownloadScreen)
                    }) { paddingValues ->
                    AnimatedContent(
                        targetState = showDownloadScreen, transitionSpec = {
                            slideInHorizontally(
                                initialOffsetX = { it }, animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeIn(
                                animationSpec = tween(300)
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { -it }, animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(
                                animationSpec = tween(300)
                            )
                        }, label = "screen_transition"
                    ) { showDownload ->
                        if (!showDownload) {
                            SetupOptionsScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues),
                                selectedOption = state.selectedOption,
                                onOptionSelected = { option, isSkip ->
                                    if (isSkip) {
                                        MainActivity.markSetupCompleted(this@SetupActivity)
                                        showRestartDialog = true
                                    } else {
                                        viewModel.selectOption(option)
                                        showDownloadScreen = true
                                    }
                                })
                        } else {
                            DownloadScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues),
                                models = state.models,
                                allDownloadsComplete = state.allDownloadsComplete,
                                onRetryDownload = { index -> viewModel.retryDownload(index) },
                                onRetryAll = { viewModel.retryFailedDownloads() },
                                onComplete = {
                                    MainActivity.markSetupCompleted(this@SetupActivity)
                                    showRestartDialog = true
                                })
                        }
                    }
                }

                // Show Restart Dialog
                if (showRestartDialog) {
                    RestartAppDialog(onRestart = {
                        restartApp()
                    }, onClose = {
                        finishAffinity()
                        exitProcess(0)
                    })
                }
            }
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
        exitProcess(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedTopBar(showDownloadScreen: Boolean) {
    val containerColor by animateColorAsState(
        targetValue = if (showDownloadScreen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface, animationSpec = tween(500), label = "topbar_color"
    )

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = containerColor
        ), title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                AnimatedContent(
                    targetState = showDownloadScreen, transitionSpec = {
                        fadeIn(tween(300)) + scaleIn(
                            initialScale = 0.8f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        ) togetherWith fadeOut(tween(200)) + scaleOut(targetScale = 1.2f)
                    }, label = "title_transition"
                ) { isDownload ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (!isDownload) "Your AI, Your Way" else "Setting Up Magic",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!isDownload) "Choose your automation style"
                            else "Preparing your AI assistant",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        })
}

@Composable
fun SetupOptionsScreen(
    modifier: Modifier = Modifier,
    selectedOption: Int?,
    onOptionSelected: (Int, Boolean) -> Unit,
) {
    val options = listOf(
        SetupOption(
            title = "Text Only",
            description = "Lucy AI for text-based automation",
            icon = Icons.Outlined.TextFields,
            badge = "Lightweight",
            isFeatured = false
        ), SetupOption(
            title = "Text + Voice Input",
            description = "Lucy AI + Whisper speech recognition",
            icon = Icons.Outlined.RecordVoiceOver,
            badge = null,
            isFeatured = false
        ), SetupOption(
            title = "Text + Voice Output",
            description = "Lucy AI + Kokoro text-to-speech",
            icon = Icons.Outlined.VolumeUp,
            badge = null,
            isFeatured = false
        ), SetupOption(
            title = "Complete AI Toolkit",
            description = "Lucy + Whisper + Kokoro for full voice & text automation",
            icon = Icons.Outlined.AutoAwesome,
            badge = "Most Popular",
            isFeatured = true
        ), SetupOption(
            title = "Voice Only Mode",
            description = "Whisper + Kokoro for hands-free experience",
            icon = Icons.Outlined.Mic,
            badge = "Hands-free",
            isFeatured = false
        )
    )

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Hero Section
            AnimatedHeroSection()

            Spacer(modifier = Modifier.height(32.dp))

            // Trust Badges
            TrustBadgesRow()

            Spacer(modifier = Modifier.height(24.dp))

            // Options
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)
            ) {
                options.forEachIndexed { index, option ->
                    MaterialExpressiveOptionCard(
                        option = option,
                        isSelected = selectedOption == index,
                        onClick = { onOptionSelected(index, false) },
                        delay = index * 80
                    )
                }
            }

            // Skip Button
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { onOptionSelected(5, true) }, modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    "Skip for now",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class SetupOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badge: String?,
    val isFeatured: Boolean = false
)

@Composable
fun AnimatedHeroSection() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = fadeIn(tween(600)) + scaleIn(
            initialScale = 0.8f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
            )
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated Brain Icon
            Box(
                modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center
            ) {
                PulsingBrainIcon()
            }
        }
    }
}

@Composable
fun PulsingBrainIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "brain_pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f, animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Surface(
        modifier = Modifier
            .size(100.dp)
            .scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(0.6f),
        tonalElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.flower),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TrustBadgesRow() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = fadeIn(tween(400)) + slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            TrustBadge(icon = Icons.Outlined.Lock, text = "Private")
            TrustBadge(icon = Icons.Outlined.ChildCare, text = "Local AI")
            TrustBadge(icon = Icons.Outlined.Security, text = "Secure")
        }
    }
}

@Composable
fun TrustBadge(icon: ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MaterialExpressiveOptionCard(
    option: SetupOption, isSelected: Boolean, onClick: () -> Unit, delay: Int
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInHorizontally(
            initialOffsetX = { -it }, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(300))
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.02f else 1f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
            ), label = "scale"
        )

        val elevation by animateDpAsState(
            targetValue = if (isSelected) 0.dp else 0.dp, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
            ), label = "elevation"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, onClick = onClick
                ), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(
                containerColor = if (option.isFeatured) MaterialTheme.colorScheme.primaryContainer.copy(
                    0.6f
                )
                else MaterialTheme.colorScheme.surfaceVariant
            ), elevation = CardDefaults.cardElevation(
                defaultElevation = elevation
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Container
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (option.isFeatured) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (option.isFeatured) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )

                        option.badge?.let { badge ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = badge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (option.isFeatured) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = 0.8f
                        )
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Arrow
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    models: List<ModelDownloadState>,
    allDownloadsComplete: Boolean,
    onRetryDownload: (Int) -> Unit,
    onRetryAll: () -> Unit,
    onComplete: () -> Unit
) {
    val currentTasks = models.filter { it.status !is DownloadStatus.Completed }
    val completedTasks = models.filter { it.status is DownloadStatus.Completed }
    val hasFailures = models.any { it.status is DownloadStatus.Failed }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with animation
            DownloadHeaderSection()

            Spacer(modifier = Modifier.height(8.dp))

            // Models download cards
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)
            ) {
                if (currentTasks.isNotEmpty()) {
                    SectionHeader(title = "In Progress", icon = Icons.Outlined.Downloading)
                    currentTasks.forEachIndexed { index, task ->
                        ExpressiveModelDownloadCard(
                            task = task,
                            delay = index * 100,
                            onRetry = { onRetryDownload(models.indexOf(task)) })
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "Completed", icon = Icons.Outlined.CheckCircle)
                    completedTasks.forEachIndexed { index, task ->
                        ExpressiveModelDownloadCard(
                            task = task, delay = (currentTasks.size + index) * 100, onRetry = {})
                    }
                }
            }

            // Action buttons
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = allDownloadsComplete,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn() + scaleIn(initialScale = 0.8f),
                exit = slideOutVertically() + fadeOut() + scaleOut()
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Text(
                            "Complete Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hasFailures && !allDownloadsComplete, enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn(), exit = slideOutVertically() + fadeOut()
            ) {
                OutlinedButton(
                    onClick = onRetryAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            "Retry Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadHeaderSection() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = fadeIn(tween(500)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DownloadAnimatedIcon()

            Text(
                text = "Setting Up Your AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This will only take a moment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DownloadAnimatedIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "download_anim")

    Surface(
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ExpressiveModelDownloadCard(
    task: ModelDownloadState, delay: Int, onRetry: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInHorizontally(
            initialOffsetX = { it }, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(300))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = when (task.status) {
                    is DownloadStatus.Completed -> MaterialTheme.colorScheme.tertiaryContainer
                    is DownloadStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Status Icon
                    when (task.status) {
                        is DownloadStatus.Downloading -> PulsingIcon()
                        is DownloadStatus.Completed -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        is DownloadStatus.Failed -> {
                            IconButton(onClick = onRetry) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        is DownloadStatus.Extracting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        else -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp), strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // Progress Indicators
                when (task.status) {
                    is DownloadStatus.Downloading -> {
                        WavyProgressBar(progress = task.progress)
                        Text(
                            text = "${(task.progress * 100).toInt()}% downloaded",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is DownloadStatus.Extracting -> {
                        WavyProgressBar(progress = task.extractionProgress)
                        Text(
                            text = "Extracting... ${(task.extractionProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    is DownloadStatus.Failed -> {
                        Text(
                            text = task.error ?: "Download failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    is DownloadStatus.Pending -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyProgressBar(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f * PI.toFloat(), animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "phase"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        ), label = "progress"
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = size.width
        val height = size.height
        val progressWidth = width * animatedProgress

        // Background wave (subtle)
        val backgroundPath = Path().apply {
            moveTo(0f, height / 2)
            var x = 0f
            while (x < width) {
                val y = height / 2 + sin(x / 40f + wavePhase) * 2f
                lineTo(x, y)
                x += 2f
            }
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = backgroundPath, color = Color.White.copy(alpha = 0.1f)
        )

        // Progress wave
        if (animatedProgress > 0f) {
            val progressPath = Path().apply {
                moveTo(0f, height / 2)
                var x = 0f
                while (x < progressWidth) {
                    val y = height / 2 + sin(x / 40f + wavePhase) * 4f
                    lineTo(x, y)
                    x += 2f
                }
                lineTo(progressWidth, height)
                lineTo(0f, height)
                close()
            }




            drawPath(
                path = progressPath, brush = Brush.horizontalGradient(
                    colors = listOf(
                        primary, tertiary
                    ), startX = 0f, endX = progressWidth
                )
            )
        }

        // Progress line on top
        drawLine(
            color = primary,
            start = Offset(0f, height / 2),
            end = Offset(progressWidth, height / 2),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f, animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Icon(
        imageVector = Icons.Outlined.CloudDownload,
        contentDescription = "Downloading",
        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        modifier = Modifier
            .size(28.dp)
            .scale(scale)
    )
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestartAppDialog(
    onRestart: () -> Unit, onClose: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Dialog(
        onDismissRequest = { }, properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = visible, enter = fadeIn(tween(300)) + scaleIn(
                initialScale = 0.8f, animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Animated Icon
                    RestartAnimatedIcon()

                    // Title & Description
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Setup Complete!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Restart the app to start using your AI assistant",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Feature highlights
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FeatureItem(
                            icon = Icons.Outlined.Speed, text = "Lightning fast responses"
                        )
                        FeatureItem(
                            icon = Icons.Outlined.Security, text = "100% private & offline"
                        )
                        FeatureItem(
                            icon = Icons.Outlined.AutoAwesome,
                            text = "Powerful AI at your fingertips"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRestart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Restart Now",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 2.dp
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Close App",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestartAnimatedIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "restart_icon")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f, animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Box(
        modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center
    ) {
        // Outer glow circle
        Surface(
            modifier = Modifier
                .size(100.dp)
                .scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {}

        // Icon container
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun FeatureItem(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}