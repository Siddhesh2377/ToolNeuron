package com.dark.neuroverse.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.ui.components.ModelDialog
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.UpdateStatus
import com.dark.neuroverse.viewModel.UpdateViewModel
import com.dark.userdata.getDefaultChatHistory
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.readBrainFile
import com.dark.userdata.saveTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.crypto.SecretKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onResetTweaks: () -> Unit = {},
) {
    val innerCorner = 8.dp
    val outerCorner = 20.dp
    var rootNode: MutableStateFlow<NeuronTree>? = null
    var key: MutableStateFlow<SecretKey>
    val chatList = MutableStateFlow(emptyList<ChatINFO>())
    val context = LocalContext.current

    val updateViewModel: UpdateViewModel = viewModel()

    LaunchedEffect(Unit) {
        key = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
        rootNode = MutableStateFlow(readBrainFile(key.value, context))

        try {
            val chatInfo = mutableListOf<ChatINFO>()
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)

            NeuronTree(history).getAllChildrenRecursive().forEach { node ->
                if (node.data.content.isNotBlank()) {
                    val title = runCatching {
                        JSONObject(node.data.content).optString("title", "Untitled")
                    }.getOrElse { "Untitled" }

                    chatInfo.add(ChatINFO(node.id, title))
                }
            }

            chatList.value = chatInfo

        } catch (e: Exception) {
            Log.e("updateChatList", "Failed loading chat titles", e)
        }
        rootNode.value.printTree()
    }

    fun clearChatHistory() {
        if (rootNode == null) return
        try {
            for (chat in chatList.value) {
                rootNode.value.deleteNodeById(chat.id)
            }
            saveTree(rootNode.value, context, BuildConfig.ALIAS)
            Log.d("clearChatHistory", "Chat history cleared")
            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("deleteChatById", "Failed to delete chats", e)
        }
    }


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                "NeuroV Settings", style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                )
            )
        }

        // ---- MODEL SETTINGS ----
        item {

            var professionalism by remember { mutableFloatStateOf(2f) }
            var emotionalTone by remember { mutableFloatStateOf(7f) }
            val currentModel = ModelManager.getModel().collectAsState()
            val context = LocalContext.current
            var expanded by remember { mutableStateOf(false) }

            LocalDensity.current
            LocalWindowInfo.current.containerSize.width.dp
            val modelList = remember { mutableStateListOf<ModelsData>() }

            LaunchedEffect(Unit) {
                val updatedProfessionalism =
                    UserPrefs.getModelPParams(context).firstOrNull() ?: 2.5f
                val updatedEmotionalTone = UserPrefs.getModelEParams(context).firstOrNull() ?: 7.3f

                professionalism = updatedProfessionalism
                emotionalTone = updatedEmotionalTone

                ModelManager.observeModels().collectLatest { data ->
                    modelList.clear()
                    modelList += data
                    Log.d("ModelManager", "Model list updated: $data")
                }
            }

            LaunchedEffect(currentModel) {
                Log.d("ModelManager", "Current model updated: ${currentModel.value}")
            }

            LaunchedEffect(professionalism, emotionalTone) {
                UserPrefs.setModelPParams(context, professionalism)
                UserPrefs.setModelEParams(context, emotionalTone)

                // Save the updated values to shared preferences or perform any other necessary actions 
                // Update the slider values when the preferences change
                val updatedProfessionalism =
                    UserPrefs.getModelPParams(context).firstOrNull() ?: 2.5f
                val updatedEmotionalTone = UserPrefs.getModelEParams(context).firstOrNull() ?: 7.3f

                professionalism = updatedProfessionalism
                emotionalTone = updatedEmotionalTone
            }


            Text(
                "Model Settings",
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
            )

            SettingCard(
                title = "Current Model", roundedCornerShape = RoundedCornerShape(
                    topStart = outerCorner,
                    topEnd = outerCorner,
                    bottomEnd = innerCorner,
                    bottomStart = innerCorner
                ), actionLabel = "Switch", onAction = {
                    expanded = true
                }) {
                if (expanded) {
                    ModelDialog(modelList, context) {
                        expanded = false
                        if (it != null) {
                            Toast.makeText(
                                context, "Model switched to ${it.modeName}", Toast.LENGTH_SHORT
                            ).show()
                            ModelManager.loadModel(context, it) {
                                Toast.makeText(
                                    context, "Model loaded successfully", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }


                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Name: ")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Normal)) {
                            append("${currentModel.value.modeName}\n\n")
                        }

                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Parameters\n")
                        }

                        append("\u2023 Context Size: ${currentModel.value.modelCtxSize}\n")
                        append("\u2023 Model Size: ${currentModel.value.modelSize} MB\n")
                        append("\u2023 Tool Call: ${currentModel.value.toolUse}")
                    }, modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingCard(
                title = "Model Tweaks", roundedCornerShape = RoundedCornerShape(
                    topStart = innerCorner,
                    topEnd = innerCorner,
                    bottomEnd = outerCorner,
                    bottomStart = outerCorner
                ), actionLabel = "Reset", onAction = onResetTweaks
            ) {
                Spacer(Modifier.height(8.dp))
                Text("Professionalism : 0.1 - 9.0")
                Slider(
                    value = professionalism, onValueChange = {
                        professionalism = it
                    }, valueRange = 0.1f..9.0f, colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    ), modifier = Modifier.fillMaxWidth(), steps = 9
                )
                Spacer(Modifier.height(8.dp))
                Text("Emotional : 0.1 - 9.0")
                Slider(
                    value = emotionalTone, onValueChange = {
                        emotionalTone = it
                    }, valueRange = 0.1f..9.0f, colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    ), modifier = Modifier.fillMaxWidth(), steps = 9
                )
            }
        }

        // ---- USER SETTINGS ----
        item {
            Text(
                "User Settings",
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
            )
            SettingCard(
                title = "Clear User Data", actionLabel = "Clear", onAction = {
                    clearChatHistory()
                })
        }

// ---- APP SETTINGS ----
        item {
            val context = LocalContext.current
            val updateInfo by updateViewModel.updateInfo.collectAsState()
            var showCard by remember { mutableStateOf(false) }

            Text(
                "App Settings",
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
            )

            SettingCard(
                title = "Check for Updates", actionLabel = when (updateInfo.status) {
                    UpdateStatus.DOWNLOADING -> "${updateInfo.downloadProgress}%"
                    UpdateStatus.READY_TO_INSTALL -> "Install"
                    UpdateStatus.IDLE -> if (updateInfo.hasUpdate) "Update" else "Check"
                    UpdateStatus.FAILED -> "Retry"
                }, showCard = showCard, onAction = {
                    when (updateInfo.status) {
                        UpdateStatus.READY_TO_INSTALL -> updateViewModel.triggerInstall(context)

                        UpdateStatus.IDLE -> {
                            updateViewModel.fetchUpdateInfo("https://raw.githubusercontent.com/Siddhesh2377/NeuroVerse/fresh-new/repo/AppUpdate.json?ts=${System.currentTimeMillis()}")
                            showCard = true

                            // 💥 After fetching, start download in 1 second to allow Flow to emit new state
                            CoroutineScope(Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(1000)
                                if (updateViewModel.updateInfo.value.hasUpdate) {
                                    updateViewModel.downloadApk(context)
                                }
                            }
                        }

                        UpdateStatus.FAILED -> {
                            updateViewModel.downloadApk(context)
                            showCard = true
                        }

                        UpdateStatus.DOWNLOADING -> {
                            // Already downloading
                        }
                    }
                }


            ) {
                Crossfade(
                    targetState = updateInfo.status, label = "UpdateStatusCrossfade"
                ) { status ->
                    when (status) {
                        UpdateStatus.IDLE -> {}

                        UpdateStatus.DOWNLOADING -> {
                            AnimatedVisibility(
                                visible = true, enter = fadeIn(), exit = fadeOut()
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                ) {
                                    Text(
                                        "Downloading...",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    val animatedProgress = animateFloatAsState(
                                        targetValue = updateInfo.downloadProgress,
                                        label = "ProgressAnim"
                                    )

                                    if (animatedProgress.value > 0f) {
                                        LinearProgressIndicator(
                                            progress = { animatedProgress.value },
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.fillMaxWidth(),
                                            strokeCap = StrokeCap.Round
                                        )
                                    }

                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("What's New:\n")
                                            }
                                            updateInfo.whatsNew.forEach {
                                                append("\u2023 $it\n")
                                            }
                                        }, modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
                        }

                        UpdateStatus.FAILED -> {
                            AnimatedVisibility(visible = true) {
                                Text(
                                    "Download failed. Please try again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        UpdateStatus.READY_TO_INSTALL -> {
                            AnimatedVisibility(visible = true) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("What's New:\n")
                                        }
                                        updateInfo.whatsNew.forEach {
                                            append("\u2023 $it\n")
                                        }
                                    }, modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

            }
        }

    }
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(18.dp),
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(roundedCornerShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction, colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    ), modifier = Modifier.height(rDP(28.dp))
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(18.dp),
    onAction: (() -> Unit)? = null,
    showCard: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = { }
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(roundedCornerShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction, colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    ), modifier = Modifier.height(rDP(28.dp))
                ) {
                    Text(actionLabel)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(showCard) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column(Modifier.padding(12.dp)) {
                    content()
                }
            }
        }
    }
}