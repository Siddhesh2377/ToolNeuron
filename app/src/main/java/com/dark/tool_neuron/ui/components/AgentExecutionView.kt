package com.dark.tool_neuron.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.messages.ToolChainStepData
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.AgentPhase

/**
 * Unified agent execution view for both streaming and persisted messages.
 * Shows 3-phase flow: Plan → Execute → Summarize.
 */
@Composable
fun AgentExecutionView(
    plan: String?,
    steps: List<ToolChainStepData>,
    summary: String?,
    phase: AgentPhase = AgentPhase.Complete,
    currentStep: Int = 0,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedStep by remember { mutableStateOf<ToolChainStepData?>(null) }

    // Tool step detail dialog
    selectedStep?.let { step ->
        ToolStepDetailDialog(
            step = step,
            onDismiss = { selectedStep = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        // Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tool),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Agent",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (phase != AgentPhase.Complete && phase != AgentPhase.Idle) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = when (phase) {
                                AgentPhase.Planning -> "Planning..."
                                AgentPhase.Executing -> "Executing..."
                                AgentPhase.Summarizing -> "Summarizing..."
                                AgentPhase.Idle, AgentPhase.Complete -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else if (steps.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "${steps.size} step${if (steps.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
            ) {
                // Phase 1: Plan
                if (plan != null) {
                    PlanSection(
                        plan = plan,
                        isActive = phase == AgentPhase.Planning
                    )
                }

                // Phase 2: Execution Steps
                if (steps.isNotEmpty() || phase == AgentPhase.Executing) {
                    ExecutionSection(
                        steps = steps,
                        isActive = phase == AgentPhase.Executing,
                        currentStep = currentStep,
                        onStepClick = { step -> selectedStep = step }
                    )
                }

                // Phase 3: Summary
                if (summary != null) {
                    SummarySection(
                        summary = summary,
                        isActive = phase == AgentPhase.Summarizing
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanSection(
    plan: String,
    isActive: Boolean
) {
    var showPlanDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive) { showPlanDialog = true },
        shape = RoundedCornerShape(rDp(8.dp)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(rDp(10.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.thinking),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(14.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Plan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isActive) {
                    PhaseSpinner()
                }
            }

            Text(
                text = plan,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showPlanDialog) {
        PlanDetailDialog(plan = plan, onDismiss = { showPlanDialog = false })
    }
}

@Composable
private fun PlanDetailDialog(
    plan: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        icon = {
            Icon(
                painter = painterResource(R.drawable.thinking),
                contentDescription = null,
                modifier = Modifier.size(rDp(24.dp)),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Agent Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = plan,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        shape = RoundedCornerShape(rDp(16.dp))
    )
}

@Composable
private fun ExecutionSection(
    steps: List<ToolChainStepData>,
    isActive: Boolean,
    currentStep: Int,
    onStepClick: (ToolChainStepData) -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(2.dp))
    ) {
        steps.forEachIndexed { index, step ->
            ExecutionStepRow(
                step = step,
                stepNumber = index + 1,
                onClick = { onStepClick(step) }
            )

            // Connector between steps
            if (index < steps.size - 1 || isActive) {
                StepConnector(isAnimated = isActive && index == steps.size - 1)
            }
        }

        // Loading indicator for next step
        if (isActive) {
            ExecutingLoadingRow()
        }
    }
}

@Composable
private fun ExecutionStepRow(
    step: ToolChainStepData,
    stepNumber: Int,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(8.dp)))
            .clickable { onClick() },
        shape = RoundedCornerShape(rDp(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(10.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.Top
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(rDp(24.dp))
                    .background(
                        color = if (step.success) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (step.success) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$stepNumber. ${step.toolName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${step.executionTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = step.pluginName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // Result preview
                if (step.result.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(rDp(4.dp)),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = step.result.take(150) + if (step.result.length > 150) "..." else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(rDp(6.dp)),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepConnector(isAnimated: Boolean = false) {
    Box(
        modifier = Modifier
            .padding(start = rDp(23.dp))
            .width(rDp(2.dp))
            .height(rDp(12.dp))
            .background(
                color = if (isAnimated) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }
            )
    )
}

@Composable
private fun ExecutingLoadingRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "exec_loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(8.dp)),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(10.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.tool),
                contentDescription = null,
                modifier = Modifier
                    .size(rDp(20.dp))
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Executing tool...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SummarySection(
    summary: String,
    isActive: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(8.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(rDp(10.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.tool),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(14.dp)),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (isActive) {
                    PhaseSpinner()
                }
            }

            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PhaseSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(rDp(12.dp)),
        strokeWidth = rDp(2.dp),
        color = MaterialTheme.colorScheme.tertiary
    )
}

/**
 * Full-screen dialog showing complete tool step details.
 * ToolNeuron-style: dark surface with sections for tool info, args, and result.
 */
@Composable
private fun ToolStepDetailDialog(
    step: ToolChainStepData,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(rDp(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = rDp(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (step.success) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                        .padding(horizontal = rDp(16.dp), vertical = rDp(12.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (step.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(20.dp)),
                            tint = if (step.success) MaterialTheme.colorScheme.tertiary
                                   else MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = step.toolName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${step.pluginName} · ${step.executionTimeMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(rDp(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
                ) {
                    // Arguments section
                    if (step.args.isNotBlank()) {
                        DetailSection(
                            title = "Arguments",
                            content = formatJson(step.args),
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(step.args))
                            }
                        )
                    }

                    // Result section
                    if (step.result.isNotBlank()) {
                        DetailSection(
                            title = if (step.success) "Result" else "Error",
                            content = formatJson(step.result),
                            isError = !step.success,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(step.result))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: String,
    isError: Boolean = false,
    onCopy: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(rDp(28.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(rDp(16.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(rDp(12.dp))
            )
        }
    }
}

/** Try to pretty-print JSON, fall back to raw string. */
private fun formatJson(raw: String): String {
    return try {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val obj = org.json.JSONTokener(trimmed).nextValue()
            when (obj) {
                is org.json.JSONObject -> obj.toString(2)
                is org.json.JSONArray -> obj.toString(2)
                else -> raw
            }
        } else raw
    } catch (_: Exception) { raw }
}
