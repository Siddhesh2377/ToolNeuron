package com.dark.tool_neuron.ui.screen.gate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewModel.VaultGateState
import com.dark.tool_neuron.viewModel.VaultGateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dark.tool_neuron.global.Standards

@Composable
fun VaultGateScreen(
    viewModel: VaultGateViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(500))
        // Auto-start migration
        viewModel.startMigration(onComplete)
    }

    LaunchedEffect(state) {
        view.keepScreenOn = state is VaultGateState.Initializing || state is VaultGateState.Migrating
    }

    // Blur overlay state
    val overlayAlpha = remember { Animatable(0f) }
    val blurAmount = remember { Animatable(0f) }
    var overlayIsError by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf("") }
    var overlayShowProgress by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val overlayColor by animateColorAsState(
        targetValue = if (overlayIsError) errorColor else primaryColor,
        animationSpec = Motion.state(),
        label = "vaultOverlay"
    )

    LaunchedEffect(state) {
        when (state) {
            is VaultGateState.Initializing -> {
                overlayIsError = false
                overlayText = "Initializing\u2026"
                overlayShowProgress = true
                launch { overlayAlpha.animateTo(1f, tween(400)) }
                launch { blurAmount.animateTo(20f, tween(400)) }
            }
            is VaultGateState.Migrating -> {
                // Dismiss overlay when migration starts showing its own progress
                launch { overlayAlpha.animateTo(0f, tween(300)) }
                launch { blurAmount.animateTo(0f, tween(300)) }
            }
            is VaultGateState.Error -> {
                overlayIsError = true
                overlayText = (state as VaultGateState.Error).message
                overlayShowProgress = false
                if (overlayAlpha.value < 0.3f) {
                    overlayAlpha.snapTo(1f)
                    blurAmount.snapTo(20f)
                }
                delay(2000)
                launch { overlayAlpha.animateTo(0f, tween(600)) }
                launch { blurAmount.animateTo(0f, tween(600)) }
            }
            else -> {
                launch { overlayAlpha.animateTo(0f, tween(300)) }
                launch { blurAmount.animateTo(0f, tween(300)) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Layer 1: Blurrable content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurAmount.value.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Standards.SpacingXl)
                    .alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = TnIcons.Shield,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = Standards.SpacingSm),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = when (state) {
                        is VaultGateState.Initializing -> "Initializing\u2026"
                        is VaultGateState.Migrating -> "Migrating Data\u2026"
                        is VaultGateState.MigrationComplete -> "Migration Complete"
                        is VaultGateState.Error -> "Error"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = when (state) {
                        is VaultGateState.Initializing -> "Setting up storage\u2026"
                        is VaultGateState.Migrating -> "Upgrading your data to the new format."
                        is VaultGateState.MigrationComplete -> "Your data has been migrated."
                        is VaultGateState.Error -> (state as VaultGateState.Error).message
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Standards.SpacingLg))

                when (state) {
                    is VaultGateState.Initializing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is VaultGateState.Migrating -> {
                        MigrationProgressContent(state as VaultGateState.Migrating)
                    }

                    is VaultGateState.MigrationComplete -> {
                        MigrationSummary(
                            state = state as VaultGateState.MigrationComplete,
                            onContinue = { viewModel.finishMigration(onComplete) }
                        )
                    }

                    is VaultGateState.Error -> {
                        val error = state as VaultGateState.Error
                        if (error.isMigration) {
                            OutlinedButton(
                                onClick = { viewModel.retryMigration(onComplete) }
                            ) {
                                Icon(
                                    TnIcons.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Retry Migration",
                                    modifier = Modifier.padding(start = Standards.SpacingSm)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.startMigration(onComplete) }
                            ) {
                                Icon(
                                    TnIcons.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Retry",
                                    modifier = Modifier.padding(start = Standards.SpacingSm)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Layer 2: Overlay
        if (overlayAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor.copy(alpha = overlayAlpha.value * 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Standards.SpacingXxl)
                        .alpha(overlayAlpha.value),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)
                ) {
                    Text(
                        text = overlayText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overlayIsError)
                            MaterialTheme.colorScheme.onError
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )

                    if (overlayShowProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ── Migration Progress ──

@Composable
private fun MigrationProgressContent(state: VaultGateState.Migrating) {
    Text(
        "Phase ${state.phase}/7 - ${state.phaseName}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(Modifier.height(Standards.SpacingSm))

    if (state.total > 0) {
        Text(
            "${state.current} / ${state.total}",
            style = MaterialTheme.typography.labelMedium
        )
        LinearProgressIndicator(
            progress = { state.current.toFloat() / state.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(Standards.SpacingMd))
    HorizontalDivider()
    Spacer(Modifier.height(Standards.SpacingSm))

    Text(
        "Log",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )

    val visibleLogs = state.logs.takeLast(20)
    for (line in visibleLogs) {
        Text(
            line,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Migration Summary ──

@Composable
private fun MigrationSummary(
    state: VaultGateState.MigrationComplete,
    onContinue: () -> Unit
) {
    Text(
        "Summary",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(Modifier.height(Standards.SpacingSm))

    Text(
        "${state.migrated} records migrated",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
    )

    if (state.skipped > 0) {
        Text(
            "${state.skipped} items skipped",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(Standards.SpacingSm))
        HorizontalDivider()
        Spacer(Modifier.height(Standards.SpacingSm))

        Text(
            "Skipped Items",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        for (failure in state.failures) {
            Text(
                failure,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    Spacer(Modifier.height(Standards.SpacingXl))

    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
}
