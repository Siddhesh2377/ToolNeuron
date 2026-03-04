package com.dark.tool_neuron.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.viewModel.SecurityMode
import com.dark.tool_neuron.viewModel.VaultGateState
import com.dark.tool_neuron.viewModel.VaultGateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VaultGateScreen(
    viewModel: VaultGateViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current

    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(500))
    }

    LaunchedEffect(state) {
        view.keepScreenOn = state is VaultGateState.Deriving || state is VaultGateState.Migrating
    }

    val visualTransformation = if (showPassword) VisualTransformation.None
                               else PasswordVisualTransformation()

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
        animationSpec = tween(250),
        label = "vaultOverlay"
    )

    LaunchedEffect(state) {
        when (state) {
            is VaultGateState.Deriving -> {
                overlayIsError = false
                overlayText = "Deriving key\u2026"
                overlayShowProgress = true
                launch { overlayAlpha.animateTo(1f, tween(400)) }
                launch { blurAmount.animateTo(20f, tween(400)) }
            }
            is VaultGateState.Error -> {
                overlayIsError = true
                overlayText = (state as VaultGateState.Error).message
                overlayShowProgress = false
                if (overlayAlpha.value < 0.3f) {
                    overlayAlpha.snapTo(1f)
                    blurAmount.snapTo(20f)
                }
                delay(500)
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
                    .padding(horizontal = 24.dp)
                    .alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header icon
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = when (state) {
                        is VaultGateState.SecuritySelection -> "Choose Security Mode"
                        is VaultGateState.Setup -> {
                            val setup = state as VaultGateState.Setup
                            if (setup.mode == SecurityMode.PROTECTED) "Set Up Vault" else "Setting Up"
                        }
                        is VaultGateState.Migrating -> "Migrating Data\u2026"
                        is VaultGateState.MigrationComplete -> "Migration Complete"
                        is VaultGateState.Unlock -> "Unlock Vault"
                        else -> "Vault"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = when (state) {
                        is VaultGateState.SecuritySelection ->
                            "How would you like to protect your data?"
                        is VaultGateState.Setup -> {
                            val setup = state as VaultGateState.Setup
                            if (setup.mode == SecurityMode.PROTECTED)
                                "Choose a passphrase to protect your data."
                            else
                                "Your data will be stored without encryption."
                        }
                        is VaultGateState.Migrating ->
                            "Upgrading your data to the new format."
                        is VaultGateState.MigrationComplete ->
                            "Your data has been migrated."
                        is VaultGateState.Unlock ->
                            "Enter your passphrase to continue."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                when (state) {
                    is VaultGateState.SecuritySelection -> {
                        SecurityModeSelection(
                            onSelectMode = { viewModel.selectMode(it) }
                        )
                    }

                    is VaultGateState.Setup -> {
                        val setup = state as VaultGateState.Setup
                        when (setup.mode) {
                            SecurityMode.REGULAR -> {
                                RegularSetupContent(
                                    onConfirm = { viewModel.confirmRegularSetup(onComplete) },
                                    onBack = { viewModel.resetToInput() }
                                )
                            }
                            SecurityMode.PROTECTED -> {
                                PassphraseInput(
                                    isSetup = true,
                                    passphrase = passphrase,
                                    confirmPassphrase = confirmPassphrase,
                                    showPassword = showPassword,
                                    visualTransformation = visualTransformation,
                                    isError = false,
                                    onPassphraseChange = { passphrase = it },
                                    onConfirmChange = { confirmPassphrase = it },
                                    onToggleVisibility = { showPassword = !showPassword },
                                    onSubmit = {
                                        focusManager.clearFocus()
                                        viewModel.submitPassphrase(passphrase, onComplete)
                                    },
                                    onBack = { viewModel.resetToInput() }
                                )
                            }
                        }
                    }

                    is VaultGateState.Unlock -> {
                        PassphraseInput(
                            isSetup = false,
                            passphrase = passphrase,
                            confirmPassphrase = confirmPassphrase,
                            showPassword = showPassword,
                            visualTransformation = visualTransformation,
                            isError = false,
                            onPassphraseChange = { passphrase = it },
                            onConfirmChange = { confirmPassphrase = it },
                            onToggleVisibility = { showPassword = !showPassword },
                            onSubmit = {
                                focusManager.clearFocus()
                                viewModel.submitPassphrase(passphrase, onComplete)
                            }
                        )
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
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Retry Migration",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        // Show passphrase input for non-migration errors (wrong passphrase)
                        if (!error.isMigration) {
                            PassphraseInput(
                                isSetup = false,
                                passphrase = passphrase,
                                confirmPassphrase = confirmPassphrase,
                                showPassword = showPassword,
                                visualTransformation = visualTransformation,
                                isError = true,
                                onPassphraseChange = { passphrase = it },
                                onConfirmChange = { confirmPassphrase = it },
                                onToggleVisibility = { showPassword = !showPassword },
                                onSubmit = {
                                    focusManager.clearFocus()
                                    viewModel.submitPassphrase(passphrase, onComplete)
                                }
                            )
                        }
                    }

                    is VaultGateState.Deriving -> { /* overlay handles this */ }
                }

                // IME spacer
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
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
                        .padding(horizontal = 32.dp)
                        .alpha(overlayAlpha.value),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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

// ── Security Mode Selection ──

@Composable
private fun SecurityModeSelection(
    onSelectMode: (SecurityMode) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onSelectMode(SecurityMode.REGULAR) },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Regular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Fast startup, no passphrase required. Data stored on-device in plaintext.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onSelectMode(SecurityMode.PROTECTED) },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Protected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "AES-256 encryption with a passphrase. Required on every launch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Regular Mode Confirmation ──

@Composable
private fun RegularSetupContent(
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        "Your data will be stored without encryption. You can change this later in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue without encryption")
    }

    TextButton(onClick = onBack) {
        Text("Go back")
    }
}

// ── Passphrase Input ──

@Composable
private fun PassphraseInput(
    isSetup: Boolean,
    passphrase: String,
    confirmPassphrase: String,
    showPassword: Boolean,
    visualTransformation: VisualTransformation,
    isError: Boolean,
    onPassphraseChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = passphrase,
        onValueChange = onPassphraseChange,
        label = { Text("Passphrase") },
        leadingIcon = {
            Icon(Icons.Rounded.Lock, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (showPassword) "Hide" else "Show"
                )
            }
        },
        visualTransformation = visualTransformation,
        isError = isError,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    if (isSetup) {
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPassphrase,
            onValueChange = onConfirmChange,
            label = { Text("Confirm Passphrase") },
            leadingIcon = {
                Icon(Icons.Rounded.Lock, contentDescription = null)
            },
            visualTransformation = visualTransformation,
            isError = confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        // Strength indicator
        val strength = when {
            passphrase.length < 8 -> "Too short (minimum 8 characters)"
            passphrase.length < 12 -> "Fair"
            else -> "Strong"
        }
        Text(
            strength,
            style = MaterialTheme.typography.labelSmall,
            color = if (strength == "Strong") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(16.dp))

    val canSubmit = if (isSetup) {
        passphrase.length >= 8 && passphrase == confirmPassphrase
    } else {
        passphrase.isNotEmpty()
    }

    AnimatedVisibility(
        visible = canSubmit,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
    ) {
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (isSetup) "Create Vault" else "Unlock",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    if (onBack != null) {
        TextButton(onClick = onBack) {
            Text("Go back")
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

    Spacer(Modifier.height(8.dp))

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

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

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

    Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

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

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
}
