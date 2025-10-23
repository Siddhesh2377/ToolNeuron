package com.dark.neuroverse.ui.screens.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.CyberViolet
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.plugins.model.Tools

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ChatInputBar(
    value: String,
    tools: List<Pair<String, List<Tools>>>,
    selectedTools: List<Tools>,
    onToolSelected: (Pair<String, Tools>) -> Unit,
    onValueChange: (String) -> Unit,
    onRag: (Boolean) -> Unit,
    onSend: () -> Unit,
    onToolRemoved: (Tools) -> Unit,
    isGenerating: Boolean,
    inputEnabled: Boolean
) {
    var showToolsList by remember { mutableStateOf(false) }
    var isRag by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .imePadding()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondary.copy(0.1f)),
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        Column(Modifier.navigationBarsPadding()) {
            // Tools list
            AnimatedVisibility(visible = showToolsList) {
                ToolsList(
                    tools = tools, onToolSelected = {
                        onToolSelected(it)
                        showToolsList = false
                    })
            }

            // Tool selection and model button row
            Row(
                modifier = Modifier
                    .padding(top = rDP(16.dp))
                    .padding(horizontal = rDP(16.dp))
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                Button(
                    onClick = {
                        if (inputEnabled) {
                            showToolsList = !showToolsList
                        }
                    }, enabled = ModelManager.currentModel.value.isToolCalling, colors = ButtonDefaults.buttonColors(
                        containerColor = if (showToolsList) SkyBlue else MaterialTheme.colorScheme.background,
                        contentColor = if (showToolsList) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    ), shape = RoundedCornerShape(rDP(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                    ) {
                        Icon(painterResource(R.drawable.tools), contentDescription = "Tools")
                        Text(text = "Tools", fontSize = rSp(14.sp))
                    }
                }

                // Selected tools chips
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(rDP(4.dp))
                ) {
                    items(selectedTools, key = { it.toolName }) { tool ->
                        ToolChip(
                            tool = tool,
                            onRemove = { onToolRemoved(tool) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // RAG toggle button
                IconButton(
                    onClick = {
                        if (inputEnabled) {
                            isRag = !isRag
                            onRag(isRag)
                        }
                    },
                    enabled = inputEnabled,
                    modifier = Modifier.size(rDP(36.dp)),
                    shape = RoundedCornerShape(rDP(8.dp)),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isRag) CyberViolet.copy(0.2f) else MaterialTheme.colorScheme.background,
                        contentColor = if (isRag) CyberViolet else MaterialTheme.colorScheme.primary,
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.database_zap),
                        contentDescription = "Toggle RAG"
                    )
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = rDP(8.dp))
                    .padding(bottom = rDP(4.dp))
                    .padding(end = rDP(18.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = inputEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = rDP(6.dp)),
                    placeholder = {
                        Text(
                            text = if (inputEnabled) "Say Anything…" else "Processing...",
                            color = SlateGrey,
                            fontSize = rSp(14.sp)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.primary, fontSize = rSp(15.sp)
                    )
                )

                Spacer(Modifier.width(rDP(8.dp)))




                Box(
                    modifier = Modifier
                        .size(rDP(36.dp))
                        .clip(CircleShape)
                        .background(
                            if (inputEnabled || isGenerating) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                        .clickable(enabled = inputEnabled || isGenerating) {
                            if (ModelManager.isModelLoaded()) {
                                onSend()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Model is not loaded..! \nPlease Load Model..!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }, contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> {
                            Icon(
                                Icons.Rounded.Stop,
                                modifier = Modifier.padding(rDP(8.dp)),
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.background
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(rDP(28.dp)),
                                trackColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                                color = MaterialTheme.colorScheme.background
                            )
                        }

                        else -> {
                            Icon(
                                painterResource(R.drawable.send_chat),
                                modifier = Modifier.padding(rDP(8.dp)),
                                contentDescription = "Send",
                                tint = if (inputEnabled) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    tool: Tools, onRemove: () -> Unit, modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0066FF)
    val backgroundColor = accentColor.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .size(ButtonDefaults.MinHeight)
            .background(color = backgroundColor, shape = RoundedCornerShape(rDP(8.dp)))
            .clickable { onRemove() }, contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Web, contentDescription = "Remove ${tool.toolName}", tint = accentColor
        )
    }
}

@Composable
fun ToolsList(
    modifier: Modifier = Modifier,
    tools: List<Pair<String, List<Tools>>>,
    onToolSelected: (Pair<String, Tools>) -> Unit
) {
    LazyColumn(
        modifier = modifier.heightIn(min = rDP(100.dp), max = rDP(300.dp)),
        contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        tools.forEach { (pluginName, toolList) ->
            item {
                Text(
                    text = pluginName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = rSp(18.sp)),
                    modifier = Modifier.padding(horizontal = rDP(16.dp), vertical = rDP(8.dp))
                )
            }

            items(toolList) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rDP(32.dp), vertical = rDP(4.dp))
                        .clickable { onToolSelected(Pair(pluginName, tool)) },
                    elevation = CardDefaults.cardElevation(rDP(0.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(modifier = Modifier.padding(rDP(12.dp))) {
                        Text(
                            text = tool.toolName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = rSp(16.sp))
                        )
                        if (tool.description.isNotBlank()) {
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = rSp(13.sp)),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}