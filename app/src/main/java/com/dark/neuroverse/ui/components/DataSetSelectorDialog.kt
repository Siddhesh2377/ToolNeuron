package com.dark.neuroverse.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp.data_hub_lib.manager.DataHubManager

@Composable
fun DataSetSelectorDialog(
    onDismiss: () -> Unit
) {
    rememberCoroutineScope()

    val dataSets by DataHubManager.installedDataSets.collectAsStateWithLifecycle(emptyList())
    val currentDataSet by DataHubManager.currentDataSet.collectAsStateWithLifecycle()

    val selectedDataSet = remember { mutableStateOf(currentDataSet?.modelName) }

    LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(text = "Choose Dataset", style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(min = 120.dp, max = 420.dp)) {
                items(dataSets) { model ->
                    DataSetRow(
                        name = model.modelName,
                        description = model.modelDescription,
                        author = model.modelAuthor,
                        created = model.modelCreated,
                        isCurrent = model.modelName == selectedDataSet.value,
                        onClick = {
                            Log.d("DataSetSelectorDialog", "Selected dataset: ${model.modelName}")
                            selectedDataSet.value = model.modelName
                            DataHubManager.setCurrentDataSet(model) {
                                if (it) {
                                    Log.d("DataSetSelectorDialog", "Dataset set successfully")
                                } else {
                                    Log.e("DataSetSelectorDialog", "Failed to set dataset")
                                }
                            }
                            onDismiss()
                        })
                    HorizontalDivider(
                        Modifier,
                        DividerDefaults.Thickness,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun DataSetRow(
    name: String,
    description: String,
    author: String,
    created: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                .size(12.dp)
        )

        Column(Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotBlank()) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Text(text = "by $author · $created", style = MaterialTheme.typography.labelSmall)
            if (isCurrent) {
                Text(
                    text = "(current)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
