package com.dark.neuroverse.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.ai_module.model.ModelData
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
                    color = colorScheme.primary
                )

                ModelSelection(viewModel, true)
            }
        }
    }, navigationIcon = {
        IconButton(
            onClick = onMenu,
            shape = RoundedCornerShape(rDP(8.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colorScheme.secondary.copy(0.1f),
                contentColor = colorScheme.secondary
            )
        ) {
            Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
        }
    }, actions = {
        IconButton(
            onClick = onLeftMenu,
            shape = RoundedCornerShape(rDP(8.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colorScheme.secondary.copy(0.1f),
                contentColor = colorScheme.secondary
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.settings), contentDescription = "New Chat"
            )
        }
    }, colors = topAppBarColors(
        containerColor = colorScheme.background
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

    LaunchedEffect(showDialog) {
        viewModel.setIsDialogOpen(showDialog)
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
                    containerColor = colorScheme.secondary.copy(0.1f),
                    contentColor = colorScheme.secondary
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
            Dialog(
                onDismissRequest = { showDialog = false }, properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                )
            ) {
                Card(
                    shape = RoundedCornerShape(rDP(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
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
                                ModelDialogItem(
                                    model = model,
                                    isSelected = isSelected,
                                    onSelect = {
                                        showDialog = false
                                        viewModel.selectModel(it)
                                    })
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

@Composable
private fun ModelDialogItem(
    model: ModelData, isSelected: Boolean, onSelect: (ModelData) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Mint.copy(alpha = 0.15f)
        else colorScheme.surfaceVariant
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(4.dp))
            .clickable { onSelect(model) },
        shape = RoundedCornerShape(rDP(12.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(rDP(0.dp))
    ) {
        Column(modifier = Modifier.padding(rDP(14.dp))) {

            // --- Top Row: Name + Selection Check ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.modelName, style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium, fontSize = rSp(16.sp)
                    ), maxLines = 2
                )

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

            Spacer(Modifier.height(rDP(4.dp)))

            // --- Subtitle ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSelected) "Currently Loaded" else "Tap to Load",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSelected) colorScheme.onSurface else Color.Gray
                    )
                )
                if (model.isToolCalling) {
                    Spacer(Modifier.width(rDP(4.dp)))
                    Icon(
                        painterResource(R.drawable.hammer),
                        "Hammer Icon",
                        tint = Color.Gray,
                        modifier = Modifier.size(rDP(12.dp))
                    )
                }
            }
        }
    }
}
