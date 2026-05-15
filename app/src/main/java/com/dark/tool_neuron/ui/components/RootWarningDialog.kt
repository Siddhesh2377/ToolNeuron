package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RootWarningDialog(
    rootEvidence: Set<String>,
    tamperEvidence: Set<String>,
    a11yPackages: Set<String>,
    onAcknowledge: () -> Unit,
) {
    val title = when {
        rootEvidence.isNotEmpty() && tamperEvidence.isNotEmpty() -> "Root and tampering signals detected"
        tamperEvidence.isNotEmpty() -> "Tampering signals detected"
        rootEvidence.isNotEmpty() -> "Root access detected"
        a11yPackages.isNotEmpty() -> "Accessibility services detected"
        else -> "Device security warning"
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Your device shows signs that lower the protection floor for ToolNeuron. The app will keep running, but read this once.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (rootEvidence.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Root access: another app on this device with root can read ToolNeuron's encrypted files and, in principle, extract session data while the app is running. Your PIN is still hardware-sealed by Android Keystore.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Evidence: ${rootEvidence.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (tamperEvidence.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Hooking framework detected (Xposed / LSPosed / similar). Modules can intercept method calls inside ToolNeuron and observe data in memory regardless of encryption.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Signals: ${tamperEvidence.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (a11yPackages.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Accessibility services from non-system apps are active. They can read on-screen text and observe what you type, including chat content.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Packages: ${a11yPackages.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Use at your own risk. This warning is shown only once.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text("I understand")
            }
        },
    )
}
