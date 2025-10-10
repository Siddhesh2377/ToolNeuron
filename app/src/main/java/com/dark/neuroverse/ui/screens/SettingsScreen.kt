package com.dark.neuroverse.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.activity.UserDataActivity
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.userdata.getDefaultChatHistory
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.readBrainFile
import com.dark.neuroverse.userdata.saveTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit = {}) {
    // UI State
    var isLoading by remember { mutableStateOf(true) }
    var chatList by remember { mutableStateOf<List<ChatINFO>>(emptyList()) }
    var clearingData by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Model settings state
    var professionalism by remember { mutableFloatStateOf(2.5f) }
    var emotionalTone by remember { mutableFloatStateOf(7.3f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentModel by ModelManager.currentModel.collectAsStateWithLifecycle()

    // Load initial data
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // Load user preferences
                professionalism = UserPrefs.getModelPParams(context).firstOrNull() ?: 2.5f
                emotionalTone = UserPrefs.getModelEParams(context).firstOrNull() ?: 7.3f

                // Load chat history
                chatList = loadChatHistory(context)
                isLoading = false
            } catch (e: Exception) {
                Log.e("SettingsScreen", "Failed to load settings", e)
                errorMessage = "Failed to load settings: ${e.message}"
                isLoading = false
            }
        }
    }

    // Save preferences when they change
    LaunchedEffect(professionalism, emotionalTone) {
        if (!isLoading) {
            UserPrefs.setModelPParams(context, professionalism)
            UserPrefs.setModelEParams(context, emotionalTone)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                Text(
                    "Settings", style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                    )
                )
            }, navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
            )
        }) { innerPadding ->
        if (isLoading) {
            LoadingContent(
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = rDP(20.dp), vertical = rDP(20.dp))
                    .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                verticalArrangement = Arrangement.spacedBy(rDP(20.dp))
            ) {
                // Error message
                errorMessage?.let { error ->
                    item {
                        ErrorCard(
                            message = error, onDismiss = { errorMessage = null })
                    }
                }

                // Model Settings Section
                item {
                    ModelSettingsSection(
                        currentModel = currentModel,
                        professionalism = professionalism,
                        emotionalTone = emotionalTone,
                        onProfessionalismChange = { professionalism = it },
                        onEmotionalToneChange = { emotionalTone = it },
                        onResetTweaks = {
                            professionalism = 2.0f
                            emotionalTone = 6.0f
                        })
                }

                // User Data Section
                item {
                    UserDataSection(
                        chatCount = chatList.size,
                        isClearingData = clearingData,
                        onClearData = {
                            scope.launch {
                                clearingData = true
                                try {
                                    clearChatHistory(context, chatList)
                                    chatList = emptyList()
                                    Toast.makeText(
                                        context, "Chat history cleared", Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "Failed to clear data", e)
                                    errorMessage = "Failed to clear data: ${e.message}"
                                } finally {
                                    clearingData = false
                                }
                            }
                        },
                        onOpenDataHub = {
                            context.startActivity(Intent(context, UserDataActivity::class.java))
                        })
                }


            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(rDP(16.dp)))
        Text(
            "Loading settings...", style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorCard(
    message: String, onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onDismiss, colors = ButtonDefaults.textButtonColors()
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun ModelSettingsSection(
    currentModel: ModelData,
    professionalism: Float,
    emotionalTone: Float,
    onProfessionalismChange: (Float) -> Unit,
    onEmotionalToneChange: (Float) -> Unit,
    onResetTweaks: () -> Unit
) {
    SectionHeader("Model Settings")

    // Current Model Info
    SettingCard(
        title = "Current Model", roundedCornerShape = RoundedCornerShape(
            topStart = rDP(22.dp),
            topEnd = rDP(22.dp),
            bottomStart = rDP(12.dp),
            bottomEnd = rDP(12.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            ModelInfoRow("Name", currentModel.modelName)
            ModelInfoRow("Context Size", "${currentModel.ctxSize}")
            ModelInfoRow("Tool Support", currentModel.isToolCalling.toString())
        }
    }

    Spacer(Modifier.height(rDP(8.dp)))

    // Model Tweaks
    SettingCard(
        title = "Model Tweaks",
        actionLabel = "Reset",
        onAction = onResetTweaks,
        roundedCornerShape = RoundedCornerShape(
            topStart = rDP(12.dp),
            topEnd = rDP(12.dp),
            bottomStart = rDP(22.dp),
            bottomEnd = rDP(22.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
        ) {
            LabeledSlider(
                label = "Professionalism",
                value = professionalism,
                range = 0.1f..9.0f,
                onChange = onProfessionalismChange
            )
            LabeledSlider(
                label = "Emotional Tone",
                value = emotionalTone,
                range = 0.1f..9.0f,
                onChange = onEmotionalToneChange
            )
        }
    }
}

@Composable
private fun ModelInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label, style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UserDataSection(
    chatCount: Int, isClearingData: Boolean, onClearData: () -> Unit, onOpenDataHub: () -> Unit
) {
    SectionHeader("User Data")

    SettingCard(
        title = "Clear Chat History",
        actionLabel = if (isClearingData) "Clearing..." else "Clear ($chatCount)",
        onAction = if (!isClearingData) onClearData else null
    ) {
        if (chatCount > 0) {
            Column(
                modifier = Modifier.padding(rDP(16.dp))
            ) {
                Text(
                    text = "This will permanently delete all your chat conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isClearingData) {
                    Spacer(Modifier.height(rDP(12.dp)))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(rDP(16.dp)), strokeWidth = rDP(2.dp)
                        )
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text(
                            "Clearing data...", style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(rDP(8.dp)))

    SettingCard(
        title = "Data Hub", actionLabel = "Open", onAction = onOpenDataHub
    ) {
        Column(
            modifier = Modifier.padding(rDP(16.dp))
        ) {
            Text(
                text = "Access your document processing and RAG data management.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title, style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.Serif, fontSize = rSp(20.sp), fontWeight = FontWeight.Bold
        ), modifier = Modifier.padding(vertical = rDP(8.dp))
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ${"%.1f".format(value)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                fontSize = rSp(14.sp)
            )
            Text(
                text = "${range.start} – ${range.endInclusive}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = rSp(12.sp)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(rDP(18.dp)),
    onAction: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(roundedCornerShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = rDP(16.dp), vertical = rDP(12.dp))
            .animateContentSize(animationSpec = tween(250))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title, style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = rSp(16.sp), fontWeight = FontWeight.Medium
                )
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction, colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        text = actionLabel, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        content?.let {
            Spacer(Modifier.height(rDP(12.dp)))
            Card(
                modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            ) {
                it()
            }
        }
    }
}

// Helper functions moved outside composable scope
private suspend fun loadChatHistory(context: Context): List<ChatINFO> =
    withContext(Dispatchers.IO) {
        try {
            val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            val rootNode = readBrainFile(key, context)
            val root = rootNode.getNodeDirect("root")
            val history = getDefaultChatHistory(root)

            val chatInfo = mutableListOf<ChatINFO>()
            NeuronTree(history).getAllChildrenRecursive().forEach { node ->
                if (node.data.content.isNotBlank()) {
                    val title = runCatching {
                        JSONObject(node.data.content).optString("title", "Untitled")
                    }.getOrElse { "Untitled" }
                    chatInfo.add(ChatINFO(node.id, title))
                }
            }
            chatInfo
        } catch (e: Exception) {
            Log.e("loadChatHistory", "Failed to load chat history", e)
            emptyList()
        }
    }

private suspend fun clearChatHistory(context: Context, chatList: List<ChatINFO>) =
    withContext(Dispatchers.IO) {
        val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
        val rootNode = readBrainFile(key, context)

        chatList.forEach { chat ->
            rootNode.deleteNodeById(chat.id)
        }

        saveTree(rootNode, context, BuildConfig.ALIAS)
        Log.d("clearChatHistory", "Cleared ${chatList.size} chats")
    }