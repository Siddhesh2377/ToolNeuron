package com.dark.tool_neuron.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.theme.rDp
import com.mp.n_apps.data.AgentConfig
import com.mp.n_apps.ui.NAppViewModel
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NAppViewModel
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initConfig(context)
    }

    val agentConfigs by viewModel.agentConfigs.collectAsState()
    val activeAgent by viewModel.activeAgent.collectAsState()
    val maxRounds by viewModel.maxRounds.collectAsState()

    var showEditSheet by remember { mutableStateOf(false) }
    var editingAgent by remember { mutableStateOf<AgentConfig?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "AI Agents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAgent = null
                    showEditSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Agent")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = rDp(Standards.SpacingLg)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            // ── Agent Settings ──
            item { SectionHeader(title = "Agent Settings") }

            item {
                MaxRoundsCard(
                    maxRounds = maxRounds,
                    onMaxRoundsChange = { viewModel.setMaxRounds(it) }
                )
            }

            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "Agents") }

            if (agentConfigs.isEmpty()) {
                item {
                    StandardCard(
                        icon = Icons.Default.Psychology,
                        title = "No Agents",
                        description = "Tap + to add your first AI agent"
                    )
                }
            } else {
                items(agentConfigs, key = { it.id }) { agent ->
                    AgentCard(
                        agent = agent,
                        isActive = agent.id == activeAgent?.id,
                        onSetActive = { viewModel.setActiveAgent(agent.id) },
                        onEdit = {
                            editingAgent = agent
                            showEditSheet = true
                        },
                        onDelete = { viewModel.deleteAgentConfig(agent.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(rDp(Standards.SpacingXl))) }
        }
    }

    if (showEditSheet) {
        AgentEditSheet(
            agent = editingAgent,
            onDismiss = { showEditSheet = false },
            onSave = { agent ->
                if (editingAgent != null) {
                    viewModel.updateAgentConfig(agent)
                } else {
                    viewModel.addAgent(agent)
                }
                showEditSheet = false
            }
        )
    }
}

// ════════════════════════════════════════
//  MaxRoundsCard
// ════════════════════════════════════════

@Composable
private fun MaxRoundsCard(
    maxRounds: Int,
    onMaxRoundsChange: (Int) -> Unit
) {
    StandardCard(title = "Max Agent Rounds") {
        Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptionText(text = "Maximum loop iterations per request")
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(rDp(4.dp))
                ) {
                    Text(
                        text = "$maxRounds",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = rDp(8.dp),
                            vertical = rDp(2.dp)
                        )
                    )
                }
            }

            Slider(
                value = maxRounds.toFloat(),
                onValueChange = { onMaxRoundsChange(it.roundToInt()) },
                valueRange = 1f..50f,
                steps = 48,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CaptionText(text = "1")
                CaptionText(text = "25")
                CaptionText(text = "50")
            }
        }
    }
}

// ════════════════════════════════════════
//  AgentCard
// ════════════════════════════════════════

@Composable
private fun AgentCard(
    agent: AgentConfig,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val maskedKey = if (agent.apiKey.length > 4) {
        "****${agent.apiKey.takeLast(4)}"
    } else {
        "****"
    }

    StandardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            RadioButton(
                selected = isActive,
                onClick = onSetActive
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${agent.modelName} · ${agent.providerUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Text(
                    text = "Key: $maskedKey",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ════════════════════════════════════════
//  AgentEditSheet
// ════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditSheet(
    agent: AgentConfig?,
    onDismiss: () -> Unit,
    onSave: (AgentConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNew = agent == null

    var name by remember { mutableStateOf(agent?.name ?: "") }
    var providerUrl by remember { mutableStateOf(agent?.providerUrl ?: "https://api.groq.com/openai") }
    var modelName by remember { mutableStateOf(agent?.modelName ?: "openai/gpt-oss-20b") }
    var apiKey by remember { mutableStateOf(agent?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && apiKey.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isNew) "New Agent" else "Edit Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Agent Name") },
                placeholder = { Text("My GPT Agent") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = providerUrl,
                onValueChange = { providerUrl = it },
                label = { Text("Provider URL") },
                placeholder = { Text("https://api.groq.com/openai") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                placeholder = { Text("openai/gpt-oss-20b") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide" else "Show"
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSave(
                            AgentConfig(
                                id = agent?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                providerUrl = providerUrl.trim(),
                                modelName = modelName.trim(),
                                apiKey = apiKey.trim(),
                                isActive = agent?.isActive ?: false
                            )
                        )
                    },
                    enabled = canSave
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
