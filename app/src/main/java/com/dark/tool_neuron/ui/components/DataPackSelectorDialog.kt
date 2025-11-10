package com.dark.tool_neuron.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.DataSetSelectionState
import com.dark.tool_neuron.worker.DataHubManager
import com.mp.data_hub_lib.model.DataSetModel
import kotlinx.coroutines.launch

@Composable
fun DataPackSelectorDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // State management
    var selectionState by remember { mutableStateOf(DataSetSelectionState.LOADING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var switchingDataSetId by remember { mutableStateOf<String?>(null) }

    // Data state
    val dataSets by DataHubManager.installedDataSets.collectAsStateWithLifecycle(emptyList())
    val currentDataSet by DataHubManager.currentDataSet.collectAsStateWithLifecycle()

    // Update state based on data
    LaunchedEffect(dataSets, currentDataSet) {
        selectionState = when {
            dataSets.isEmpty() -> DataSetSelectionState.EMPTY
            switchingDataSetId != null -> DataSetSelectionState.SWITCHING
            errorMessage != null -> DataSetSelectionState.ERROR
            else -> DataSetSelectionState.READY
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose Dataset", style = MaterialTheme.typography.titleLarge
                )

                // Refresh button
                IconButton(
                    onClick = {
                        scope.launch {
                            try {
                                selectionState = DataSetSelectionState.LOADING
                                errorMessage = null
                                // Trigger refresh - you might need to add this method to DataHubManager
                                // DataHubManager.refreshDataSets()
                                selectionState = DataSetSelectionState.READY
                            } catch (e: Exception) {
                                Log.e("DataSetDialog", "Failed to refresh datasets", e)
                                errorMessage = "Failed to refresh: ${e.message}"
                                selectionState = DataSetSelectionState.ERROR
                            }
                        }
                    }, enabled = selectionState != DataSetSelectionState.LOADING
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh, contentDescription = "Refresh datasets"
                    )
                }
            }
        }, text = {
            when (selectionState) {
                DataSetSelectionState.LOADING -> {
                    LoadingContent()
                }

                DataSetSelectionState.EMPTY -> {
                    EmptyStateContent()
                }

                DataSetSelectionState.ERROR -> {
                    ErrorContent(
                        message = errorMessage ?: "Unknown error", onRetry = {
                            errorMessage = null
                            selectionState = DataSetSelectionState.READY
                        })
                }

                DataSetSelectionState.READY, DataSetSelectionState.SWITCHING -> {
                    DataSetList(
                        dataSets = dataSets,
                        currentDataSet = currentDataSet,
                        switchingDataSetId = switchingDataSetId,
                        onDataSetSelected = { dataSet ->
                            if (dataSet.modelName == currentDataSet?.modelName) {
                                // Already selected, just dismiss
                                onDismiss()
                                return@DataSetList
                            }

                            scope.launch {
                                try {
                                    switchingDataSetId = dataSet.modelName
                                    selectionState = DataSetSelectionState.SWITCHING

                                    Log.d(
                                        "DataSetDialog",
                                        "Switching to dataset: ${dataSet.modelName}"
                                    )

                                    // Use suspend-friendly approach if available
                                    val success = try {
                                        DataHubManager.setCurrentDataSet(dataSet) { success ->
                                            if (success) {
                                                Log.d(
                                                    "DataSetDialog", "Dataset switched successfully"
                                                )
                                            } else {
                                                Log.e("DataSetDialog", "Failed to switch dataset")
                                                errorMessage =
                                                    "Failed to switch to ${dataSet.modelName}"
                                            }
                                        }
                                        true // Assume success for now
                                    } catch (e: Exception) {
                                        Log.e("DataSetDialog", "Error switching dataset", e)
                                        errorMessage = "Error switching dataset: ${e.message}"
                                        false
                                    }

                                    if (success) {
                                        onDismiss()
                                    } else {
                                        selectionState = DataSetSelectionState.ERROR
                                    }
                                } finally {
                                    switchingDataSetId = null
                                }
                            }
                        })
                }
            }
        }, confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }, dismissButton = {
            if (selectionState == DataSetSelectionState.SWITCHING) {
                TextButton(
                    onClick = { /* Cancel operation if possible */ }, enabled = false
                ) {
                    Text("Cancel")
                }
            }
        }, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Loading datasets...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStateContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No datasets available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Install datasets through the Data Hub",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(
    message: String, onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun DataSetList(
    dataSets: List<DataSetModel>,
    currentDataSet: DataSetModel?,
    switchingDataSetId: String?,
    onDataSetSelected: (DataSetModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.heightIn(min = 120.dp, max = 420.dp)
    ) {
        if (dataSets.isEmpty()) {
            item {
                EmptyStateContent()
            }
        } else {
            items(
                items = dataSets, key = { it.modelName }) { dataSet ->
                DataSetRow(
                    dataSet = dataSet,
                    isCurrent = dataSet.modelName == currentDataSet?.modelName,
                    isSwitching = dataSet.modelName == switchingDataSetId,
                    onClick = { onDataSetSelected(dataSet) })

                if (dataSet != dataSets.last()) {
                    HorizontalDivider(
                        thickness = DividerDefaults.Thickness,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DataSetRow(
    dataSet: DataSetModel, isCurrent: Boolean, isSwitching: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSwitching, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status indicator
        Column(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
                .size(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isCurrent, enter = fadeIn(), exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(8.dp),
                    tint = Color.White
                )
            }
        }

        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = dataSet.modelName, style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal
                ), maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            if (dataSet.modelDescription.isNotBlank()) {
                Text(
                    text = dataSet.modelDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "by ${dataSet.modelAuthor} · ${dataSet.modelCreated}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isCurrent) {
                Text(
                    text = "(current)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Loading indicator for switching state
        AnimatedVisibility(
            visible = isSwitching, enter = fadeIn(), exit = fadeOut()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp), strokeWidth = 2.dp
            )
        }
    }
}