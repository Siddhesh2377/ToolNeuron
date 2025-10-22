package com.dark.neuroverse.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.viewModel.setupScreen.DownloadStatus
import com.dark.neuroverse.viewModel.setupScreen.ModelDownloadState
import com.dark.neuroverse.viewModel.setupScreen.SetupViewModel
import kotlinx.coroutines.delay

class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NeuroVerseTheme {
                val state by viewModel.state.collectAsState()
                var showDownloadScreen by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Setup-Screen",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        if (!showDownloadScreen) "Select an option to continue"
                                        else "Downloading Models",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = MaterialTheme.colorScheme.primary.copy(0.7f),
                                        )
                                    )
                                }
                            })
                    }) { paddingValues ->
                    AnimatedContent(
                        targetState = showDownloadScreen, transitionSpec = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn() togetherWith slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut()
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

                                        // Navigate to MainActivity
                                        startActivity(
                                            Intent(
                                                this@SetupActivity,
                                                MainActivity::class.java
                                            ).apply {
                                                flags =
                                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            })
                                        finish()
                                    }
                                    viewModel.selectOption(option)
                                    showDownloadScreen = true
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

                                    // Navigate to MainActivity
                                    startActivity(
                                        Intent(this@SetupActivity, MainActivity::class.java).apply {
                                            flags =
                                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        })
                                    finish()
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupOptionsScreen(
    modifier: Modifier = Modifier,
    selectedOption: Int?,
    onOptionSelected: (Int, Boolean) -> Unit,
) {
    val options = listOf(
        "Text" to "Basic text processing",
        "Text + STT" to "Speech-to-text included",
        "Text + TTS" to "Text-to-speech included",
        "Text + STT + TTS" to "Full voice features",
        "Skip" to "Start Fresh..!"
    )

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        options.forEachIndexed { index, (title, description) ->
            AnimatedSetupOption(
                title = title,
                description = description,
                isSelected = selectedOption == index,
                onClick = { onOptionSelected(index, index == options.lastIndex) },
                delay = index * 100
            )
        }
    }
}

@Composable
fun AnimatedSetupOption(
    title: String, description: String, isSelected: Boolean, onClick: () -> Unit, delay: Int
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn()
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.02f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "scale"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary.copy(0.7f)
                    )
                }

                Icon(
                    painterResource(R.drawable.next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedDownloadCard(
                title = getSelectedOptionTitle(models.size),
                currentTasks = currentTasks,
                completedTasks = completedTasks,
                onRetryDownload = onRetryDownload
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons at bottom
            AnimatedVisibility(
                visible = allDownloadsComplete,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn() + scaleIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut() + scaleOut()
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle, contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Complete Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hasFailures && !allDownloadsComplete,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn() + scaleIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut() + scaleOut()
            ) {
                OutlinedButton(
                    onClick = onRetryAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Retry Failed Downloads",
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
fun AnimatedDownloadCard(
    title: String,
    currentTasks: List<ModelDownloadState>,
    completedTasks: List<ModelDownloadState>,
    onRetryDownload: (Int) -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (currentTasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Current Tasks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        currentTasks.forEachIndexed { index, task ->
                            AnimatedTaskItem(
                                task = task,
                                delay = index * 150,
                                onRetry = { onRetryDownload(index) })
                        }
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Completed Tasks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        completedTasks.forEachIndexed { index, task ->
                            AnimatedTaskItem(
                                task = task,
                                delay = (currentTasks.size + index) * 150,
                                onRetry = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTaskItem(
    task: ModelDownloadState, delay: Int, onRetry: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = when (task.status) {
                    is DownloadStatus.Completed -> MaterialTheme.colorScheme.tertiaryContainer
                    is DownloadStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    when (task.status) {
                        is DownloadStatus.Downloading -> PulsingDownloadIndicator()
                        is DownloadStatus.Completed -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        is DownloadStatus.Failed -> {
                            IconButton(onClick = onRetry) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        is DownloadStatus.Pending -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                            )
                        }

                        DownloadStatus.Extracting -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "Extracting...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                // Show error message if failed
                if (task.status is DownloadStatus.Failed) {
                    Text(
                        text = task.error ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Show progress bar for downloading and pending
                when (task.status) {
                    is DownloadStatus.Downloading -> {
                        AnimatedLinearProgressIndicator(progress = task.progress)
                        Text(
                            text = "${(task.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    is DownloadStatus.Pending -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    is DownloadStatus.Extracting -> {
                        AnimatedLinearProgressIndicator(progress = task.extractionProgress)
                        Text(
                            text = "Extracting... ${(task.extractionProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun PulsingDownloadIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(
            animation = tween(1000), repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Text(
        text = "Downloading...",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun AnimatedLinearProgressIndicator(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    )
}

fun getSelectedOptionTitle(modelCount: Int): String {
    return when (modelCount) {
        1 -> "Text"
        2 -> "Text + STT" // or "Text + TTS"
        3 -> "Text + STT + TTS"
        else -> "Models"
    }
}