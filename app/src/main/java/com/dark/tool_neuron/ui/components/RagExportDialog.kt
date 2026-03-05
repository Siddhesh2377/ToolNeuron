package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.ui.theme.rDp
import com.neuronpacket.LoadingMode
import com.dark.tool_neuron.ui.icons.TnIcons

/**
 * Export configuration data class matching NeuronPacket ExportConfig
 */
data class RagExportConfig(
    val adminPassword: String,
    val readOnlyUsers: List<Pair<String, String>> = emptyList(), // username to password
    val loadingMode: LoadingMode = LoadingMode.TRANSIENT,
    val enableCompression: Boolean = true,
    val packetName: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "1.0"
)

@Composable
fun RagExportDialog(
    rag: InstalledRag,
    onDismiss: () -> Unit,
    onExport: (RagExportConfig) -> Unit
) {
    var adminPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loadingMode by remember { mutableStateOf(LoadingMode.TRANSIENT) }
    var enableCompression by remember { mutableStateOf(true) }
    var addReadOnlyUsers by remember { mutableStateOf(false) }
    val readOnlyUsers = remember { mutableStateListOf<Pair<String, String>>() }
    var currentReadOnlyUsername by remember { mutableStateOf("") }
    var currentReadOnlyPassword by remember { mutableStateOf("") }
    var packetName by remember { mutableStateOf(rag.name) }
    var description by remember { mutableStateOf(rag.description) }
    var author by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0") }

    var passwordError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Export RAG as NeuronPacket",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                // Packet Info Section
                SectionHeader(title = "Packet Information")

                OutlinedTextField(
                    value = packetName,
                    onValueChange = { packetName = it },
                    label = { Text("Packet Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = version,
                        onValueChange = { version = it },
                        label = { Text("Version") },
                        modifier = Modifier.width(rDp(80.dp)),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(rDp(8.dp)))

                // Security Section
                SectionHeader(title = "Security")

                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = {
                        adminPassword = it
                        passwordError = null
                    },
                    label = { Text("Admin Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(TnIcons.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) TnIcons.EyeOff else TnIcons.Eye,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    isError = passwordError != null
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        passwordError = null
                    },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(TnIcons.Lock, contentDescription = null)
                    },
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )

                // Read-only users section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Read-Only Users",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = addReadOnlyUsers,
                        onCheckedChange = { addReadOnlyUsers = it }
                    )
                }

                AnimatedVisibility(visible = addReadOnlyUsers) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        // Display existing read-only users
                        readOnlyUsers.forEachIndexed { index, (username, _) ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(rDp(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(rDp(8.dp)),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                                    ) {
                                        Icon(
                                            TnIcons.User,
                                            contentDescription = null,
                                            modifier = Modifier.size(rDp(20.dp))
                                        )
                                        Text(
                                            text = username,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { readOnlyUsers.removeAt(index) }
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }

                        // Add new read-only user
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentReadOnlyUsername,
                                onValueChange = { currentReadOnlyUsername = it },
                                label = { Text("Username") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = currentReadOnlyPassword,
                                onValueChange = { currentReadOnlyPassword = it },
                                label = { Text("Password") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (currentReadOnlyUsername.isNotBlank() && currentReadOnlyPassword.isNotBlank()) {
                                    readOnlyUsers.add(currentReadOnlyUsername to currentReadOnlyPassword)
                                    currentReadOnlyUsername = ""
                                    currentReadOnlyPassword = ""
                                }
                            },
                            enabled = currentReadOnlyUsername.isNotBlank() && currentReadOnlyPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add User")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(rDp(8.dp)))

                // Export Options Section
                SectionHeader(title = "Export Options")

                // Loading Mode
                Text(
                    text = "Loading Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Column {
                    LoadingModeOption(
                        title = "Transient",
                        description = "Data is loaded on demand and unloaded when not needed. Lower memory usage.",
                        selected = loadingMode == LoadingMode.TRANSIENT,
                        onClick = { loadingMode = LoadingMode.TRANSIENT }
                    )
                    LoadingModeOption(
                        title = "Embedded",
                        description = "Data is embedded in memory. Faster access but higher memory usage.",
                        selected = loadingMode == LoadingMode.EMBEDDED,
                        onClick = { loadingMode = LoadingMode.EMBEDDED }
                    )
                }

                // Compression
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Enable Compression",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Reduces file size using LZ4 compression",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = enableCompression,
                        onCheckedChange = { enableCompression = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validate passwords
                    if (adminPassword.length < 6) {
                        passwordError = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (adminPassword != confirmPassword) {
                        passwordError = "Passwords do not match"
                        return@Button
                    }

                    val config = RagExportConfig(
                        adminPassword = adminPassword,
                        readOnlyUsers = readOnlyUsers.toList(),
                        loadingMode = loadingMode,
                        enableCompression = enableCompression,
                        packetName = packetName,
                        description = description,
                        author = author,
                        version = version
                    )
                    onExport(config)
                },
                enabled = adminPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LoadingModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(rDp(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}