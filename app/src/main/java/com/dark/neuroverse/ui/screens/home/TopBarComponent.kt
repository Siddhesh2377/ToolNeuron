package com.dark.neuroverse.ui.screens.home

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.ai_module.model.ModelType
import com.dark.neuroverse.R
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.worker.UIStateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    viewModel: ChatScreenViewModel, onMenu: () -> Unit = {}, onLeftMenu: () -> Unit = {}
) {
    val title by viewModel.chatTitle.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    CenterAlignedTopAppBar(
        title = {
            if (messages.isEmpty()) {
                ModelSelection(viewModel, false)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ModelSelection(viewModel, true)
                }
            }
        }, navigationIcon = {
            IconButton(
                onClick = onMenu,
                shape = RoundedCornerShape(rDP(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
            }
        }, actions = {
            IconButton(
                onClick = onLeftMenu,
                shape = RoundedCornerShape(rDP(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings), contentDescription = "New Chat"
                )
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
@Composable
fun ModelSelection(viewModel: ChatScreenViewModel, isCompact: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    val modelList by viewModel.modelList.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val uiState by UIStateManager.uiState.collectAsStateWithLifecycle()
    val isGeneratingTitle = uiState is ChatUiState.GeneratingTitle

    val filteredModellist = modelList.filter {
        it.modelType == ModelType.TEXT
    }

    val selectedModelName = remember(selectedModel) {
        if (selectedModel.modelName == "") "Select Model"
        else selectedModel.modelName
    }

    Column {
        if (isCompact) {
            IconButton(
                onClick = { if (!isGeneratingTitle) showDialog = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colorScheme.secondary.copy(0.1f),
                    contentColor = colorScheme.secondary
                ),
                shape = RoundedCornerShape(rDP(8.dp)),
            ) {
                Icon(Icons.Outlined.SmartToy, "Model")
            }
        } else {
            Button(
                onClick = { if (!isGeneratingTitle) showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(rDP(8.dp)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SmartToy, "Model")
                    Spacer(modifier = Modifier.width(rDP(8.dp)))
                    Text(
                        text = selectedModelName, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(rDP(8.dp)))
                    Icon(Icons.Default.KeyboardArrowDown, "Expand")
                }
            }
        }

        if (showDialog) {
            Dialog(onDismissRequest = { showDialog = false }) {
                Card(
                    shape = RoundedCornerShape(rDP(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(rDP(16.dp))
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Select Model",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = rDP(12.dp))
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(min = rDP(150.dp), max = rDP(320.dp)),
                        ) {
                            items(filteredModellist) { model ->
                                val isSelected = model.modelName == selectedModel.modelName
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = rDP(1.5.dp))
                                        .clickable {
                                            Log.d("ModelSelection", "Selected model: $model")
                                            viewModel.selectModel(model)
                                        }, colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Mint.copy(alpha = 0.15f)
                                        else colorScheme.secondary.copy(0.1f)
                                    ), elevation = CardDefaults.cardElevation(rDP(0.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(rDP(14.dp))
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = model.modelName,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = rSp(16.sp)
                                                )
                                            )
                                            Text(
                                                text = if (isSelected) "Currently Loaded" else "Tap to Load",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = if (isSelected) Color.Unspecified else Color.Gray
                                                )
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Loaded",
                                                tint = Mint,
                                                modifier = Modifier.size(rDP(20.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(rDP(12.dp)))
                        Button(
                            onClick = { showDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(rDP(8.dp))
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}