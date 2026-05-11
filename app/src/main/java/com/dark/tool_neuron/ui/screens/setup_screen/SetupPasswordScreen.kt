package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.PinAction
import com.dark.tool_neuron.ui.components.PinActionRow
import com.dark.tool_neuron.ui.components.PinDotRow
import com.dark.tool_neuron.ui.components.PinNumberPad
import com.dark.tool_neuron.ui.components.SecureScreen
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun SetupPasswordScreen(
    innerPadding: PaddingValues,
    password: String,
    isConfirmStep: Boolean,
    error: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    SecureScreen {
        SetupPasswordScreenContent(innerPadding, password, isConfirmStep, error, onDigit, onDelete, onClear, onSubmit, onBack)
    }
}

@Composable
private fun SetupPasswordScreenContent(
    innerPadding: PaddingValues,
    password: String,
    isConfirmStep: Boolean,
    error: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = TnIcons.OAuth,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(dimens.spacingMd))

                Text(
                    text = if (isConfirmStep) "Confirm your password" else "Create a password",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(dimens.spacingXs))

                Text(
                    text = if (isConfirmStep) "Enter the same password again" else "At least 6 digits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinDotRow(length = password.length)

                Spacer(Modifier.height(dimens.spacingSm))

                val errorAlpha by animateFloatAsState(
                    targetValue = if (error != null) 1f else 0f,
                    animationSpec = Motion.state(),
                    label = "setupErrorAlpha"
                )
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(20.dp).alpha(errorAlpha)
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 2 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinNumberPad(onDigit = onDigit, onDelete = onDelete)

                Spacer(Modifier.height(dimens.spacingMd))

                PinActionRow(
                    actions = listOf(
                        PinAction("Back", onBack),
                        PinAction("Clear", onClear),
                        PinAction(
                            label = if (isConfirmStep) "Confirm" else "Next",
                            onClick = onSubmit,
                            enabled = password.length >= 4,
                            primary = true
                        )
                    )
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
