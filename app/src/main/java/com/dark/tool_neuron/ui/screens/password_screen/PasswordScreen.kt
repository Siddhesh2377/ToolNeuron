package com.dark.tool_neuron.ui.screens.password_screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
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
fun PasswordScreen(
    innerPadding: PaddingValues,
    password: String,
    error: String?,
    isVerifying: Boolean,
    lockedUntilMs: Long,
    wiped: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    SecureScreen {
        val now by produceState(System.currentTimeMillis(), lockedUntilMs, wiped) {
            while (!wiped && lockedUntilMs > System.currentTimeMillis()) {
                value = System.currentTimeMillis()
                delay(500)
            }
            value = System.currentTimeMillis()
        }
        when {
            wiped -> WipedScreen(innerPadding)
            lockedUntilMs > now -> LockedOutScreen(innerPadding, remainingMs = lockedUntilMs - now)
            else -> PasswordScreenContent(
                innerPadding, password, error, isVerifying, onDigit, onDelete, onClear, onSubmit,
            )
        }
    }
}

@Composable
private fun LockedOutScreen(innerPadding: PaddingValues, remainingMs: Long) {
    LockoutSurface(
        innerPadding = innerPadding,
        title = "Locked",
        body = "Too many wrong PINs. Try again in ${formatRemaining(remainingMs)}.",
        primary = null,
    )
}

@Composable
private fun WipedScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    LockoutSurface(
        innerPadding = innerPadding,
        title = "Vault wiped",
        body = "All local data was erased. Tap Restart to set up the app again.",
        primary = "Restart" to {
            (context as? android.app.Activity)?.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        },
    )
}

@Composable
private fun LockoutSurface(
    innerPadding: PaddingValues,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>?,
) {
    val dimens = LocalDimens.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = TnIcons.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(dimens.spacingMd))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(dimens.spacingSm))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (primary != null) {
                    Spacer(Modifier.height(dimens.spacingLg))
                    Button(
                        onClick = primary.second,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(primary.first) }
                }
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "%d h %02d m".format(h, m)
        m > 0 -> "%d m %02d s".format(m, s)
        else -> "${s}s"
    }
}

@Composable
private fun PasswordScreenContent(
    innerPadding: PaddingValues,
    password: String,
    error: String?,
    isVerifying: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
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
                    text = "Enter your password",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(dimens.spacingXs))

                Text(
                    text = "Unlock to continue",
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
                    label = "errorAlpha"
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
                PinNumberPad(
                    onDigit = onDigit,
                    onDelete = onDelete,
                    enabled = !isVerifying
                )

                Spacer(Modifier.height(dimens.spacingMd))

                PinActionRow(
                    actions = listOf(
                        PinAction("Clear", onClear, enabled = !isVerifying),
                        PinAction("Unlock", onSubmit, enabled = !isVerifying && password.length >= 4, primary = true)
                    )
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
