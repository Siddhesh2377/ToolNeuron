package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.activity.ScrapActivity
import com.dark.neuroverse.activity.TempActivity
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.components.ModelDialog
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.CyberViolet
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.UpdateStatus
import com.dark.neuroverse.viewModel.UpdateViewModel
import com.dark.userdata.getDefaultChatHistory
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.readBrainFile
import com.dark.userdata.saveTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import javax.crypto.SecretKey

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun SettingsScreen(
) {
    // —— Spacing tokens ——
    val screenPadding = rDP(20.dp)
    val sectionSpacing = rDP(20.dp)
    val innerCorner = rDP(12.dp)
    val outerCorner = rDP(22.dp)

    // —— Brain state ——
    lateinit var key: MutableStateFlow<SecretKey>
    var rootNode: MutableStateFlow<NeuronTree>? = null
    val chatList = remember { MutableStateFlow(emptyList<ChatINFO>()) }
    val context = LocalContext.current

    val updateViewModel: UpdateViewModel = viewModel()
    val updateInfo by updateViewModel.updateInfo.collectAsState()

    var isChecking by remember { mutableStateOf(false) }

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

    LaunchedEffect(updateInfo.status, updateInfo.hasUpdate) {
        if (updateInfo.status != UpdateStatus.IDLE) isChecking = false
        if (updateInfo.status == UpdateStatus.IDLE && !updateInfo.hasUpdate) isChecking = false
    }

    fun clearChatHistory() {
        val rn = rootNode ?: return
        try {
            for (chat in chatList.value) {
                rn.value.deleteNodeById(chat.id)
            }
            saveTree(rn.value, context, BuildConfig.ALIAS)
            Log.d("clearChatHistory", "Chat history cleared")
            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("deleteChatById", "Failed to delete chats", e)
        }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = screenPadding, vertical = screenPadding)
                .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            item {
                Text(
                    "NeuroV Settings",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = rSp(26.sp)
                    )
                )
            }

            // ——— MODEL SETTINGS ———
            item {
                var professionalism by remember { mutableFloatStateOf(2.5f) }
                var emotionalTone by remember { mutableFloatStateOf(7.3f) }
                val currentModel = ModelManager.getModel().collectAsState()
                val ctx = context
                var showModelPicker by remember { mutableStateOf(false) }
                val modelList = remember { mutableStateListOf<ModelsData>() }

                LaunchedEffect(Unit) {
                    professionalism = UserPrefs.getModelPParams(ctx).firstOrNull() ?: 2.5f
                    emotionalTone = UserPrefs.getModelEParams(ctx).firstOrNull() ?: 7.3f

                    ModelManager.observeModels().collectLatest { data ->
                        modelList.clear(); modelList += data
                    }
                }

                LaunchedEffect(professionalism, emotionalTone) {
                    UserPrefs.setModelPParams(ctx, professionalism)
                    UserPrefs.setModelEParams(ctx, emotionalTone)
                }

                SectionHeader("Model Settings")

//                SettingCard(
//                    title = "Current Model",
//                    roundedCornerShape = RoundedCornerShape(
//                        topStart = outerCorner,
//                        topEnd = outerCorner,
//                        bottomEnd = innerCorner,
//                        bottomStart = innerCorner
//                    ),
//                    actionLabel = "Switch",
//                    onAction = { showModelPicker = true }
//                ) {
//                    if (showModelPicker) {
//                        ModelDialog(modelList) { selected ->
//                            showModelPicker = false
//                            selected?.let {
//                                Toast.makeText(
//                                    ctx, "Model switched to ${it.modeName}", Toast.LENGTH_SHORT
//                                ).show()
//                                ModelManager.loadModelAwait(modelData = it, onLoaded = {
//                                    Toast.makeText(
//                                        ctx, "Model loaded successfully", Toast.LENGTH_SHORT
//                                    ).show()
//                                })
//                            }
//                        }
//                    }
//
//                    Text(
//                        buildAnnotatedString {
//                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Name: ") }
//                            append("${currentModel.value.modeName}\n\n")
//
//                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Parameters\n") }
//                            append("• Context Size: ${currentModel.value.modelCtxSize}\n")
//                            append("• Model Size: ${currentModel.value.modelSize} MB\n")
//                            append("• Tool Call: ${currentModel.value.toolUse}")
//                        },
//                        modifier = Modifier.padding(rDP(12.dp)),
//                        fontSize = rSp(14.sp)
//                    )
//                }

                Spacer(Modifier.height(rDP(8.dp)))

                SettingCard(
                    title = "Model Tweaks",
                    roundedCornerShape = RoundedCornerShape(
                        topStart = innerCorner,
                        topEnd = innerCorner,
                        bottomEnd = outerCorner,
                        bottomStart = outerCorner
                    ),
                    actionLabel = "Reset",
                    onAction = {
                        professionalism = 2.0f
                        emotionalTone = 6.0f
                    }
                ) {
                    Column(
                        Modifier.padding(rDP(16.dp)),
                        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                    ) {
                        LabeledSlider(
                            label = "Professionalism",
                            value = professionalism,
                            range = 0.1f..9.0f,
                            onChange = { professionalism = it }
                        )
                        LabeledSlider(
                            label = "Emotional",
                            value = emotionalTone,
                            range = 0.1f..9.0f,
                            onChange = { emotionalTone = it }
                        )
                    }
                }
            }

            // ——— USER SETTINGS ———
            item {
                SectionHeader("User Settings")
                SettingCard(
                    title = "Clear User Data",
                    actionLabel = "Clear",
                    onAction = { clearChatHistory() }
                )

                Spacer(Modifier.height(rDP(8.dp)))
                SettingCard(
                    title = "Data Hub",
                    actionLabel = "Open",
                    onAction = {
                        context.startActivity(Intent(context, ScrapActivity::class.java))
                    }
                )
            }

//            // ——— APP SETTINGS ———
//            item {
//                SectionHeader("App Settings")
//
//                var showCard by remember { mutableStateOf(true) }
//
//                val actionLabel = when (updateInfo.status) {
//                    UpdateStatus.DOWNLOADING -> "${(updateInfo.downloadProgress * 100).toInt()}%"
//                    UpdateStatus.READY_TO_INSTALL -> "Install"
//                    UpdateStatus.FAILED -> "Retry"
//                    UpdateStatus.IDLE -> when {
//                        isChecking -> "Checking…"
//                        updateInfo.hasUpdate -> "Update"
//                        else -> "Check"
//                    }
//                }
//
//                SettingCard(
//                    title = "Check for Updates",
//                    actionLabel = actionLabel,
//                    showCard = showCard,
//                    onAction = {
//                        when (updateInfo.status) {
//                            UpdateStatus.READY_TO_INSTALL -> updateViewModel.triggerInstall(context)
//                            UpdateStatus.IDLE -> {
//                                isChecking = true
//                                updateViewModel.checkForUpdateAndStartDownload()
//                                showCard = true
//                            }
//                            UpdateStatus.FAILED -> {
//                                updateViewModel.downloadApk(context)
//                                showCard = true
//                            }
//                            UpdateStatus.DOWNLOADING -> Unit
//                        }
//                    }
//                ) {
//                    AnimatedContent(
//                        targetState = Triple(updateInfo.status, updateInfo.hasUpdate, isChecking),
//                        transitionSpec = {
//                            slideInVertically(animationSpec = tween(220)) { it / 2 } + fadeIn() togetherWith
//                                    slideOutVertically(animationSpec = tween(220)) { -it / 2 } + fadeOut()
//                        },
//                        label = "UpdateStatusAnimatedContent"
//                    ) { (status, hasUpdate, checking) ->
//                        var checkTimedOut by remember { mutableStateOf(false) }
//                        LaunchedEffect(checking, status, hasUpdate) {
//                            if (checking) {
//                                checkTimedOut = false
//                                kotlinx.coroutines.delay(10_000)
//                                if (isChecking && status == UpdateStatus.IDLE && !hasUpdate) {
//                                    checkTimedOut = true
//                                    isChecking = false
//                                }
//                            } else {
//                                checkTimedOut = false
//                            }
//                        }
//
//                        when (status) {
//                            UpdateStatus.IDLE -> {
//                                if (checking) {
//                                    Column(Modifier.padding(rDP(16.dp))) {
//                                        Text(
//                                            "Checking for updates…",
//                                            style = MaterialTheme.typography.titleMedium.copy(
//                                                color = CyberViolet,
//                                                fontSize = rSp(16.sp)
//                                            )
//                                        )
//                                        Spacer(Modifier.height(rDP(8.dp)))
//                                        LinearWavyProgressIndicator(
//                                            modifier = Modifier.fillMaxWidth(),
//                                        )
//                                        Spacer(Modifier.height(rDP(12.dp)))
//                                        SubtleNote("Hang tight while we ping the mothership.")
//                                    }
//                                } else if (hasUpdate) {
//                                    Column(Modifier.padding(rDP(16.dp))) {
//                                        Text(
//                                            "Update available",
//                                            style = MaterialTheme.typography.titleLarge.copy(
//                                                fontWeight = FontWeight.SemiBold,
//                                                color = Coral,
//                                                fontSize = rSp(20.sp)
//                                            )
//                                        )
//                                        Spacer(Modifier.height(rDP(6.dp)))
//                                        MarkdownText("Tap **Update** to download the latest build.")
//                                    }
//                                } else if (checkTimedOut) {
//                                    Column(Modifier.padding(rDP(16.dp))) {
//                                        Text(
//                                            "No update found",
//                                            style = MaterialTheme.typography.titleLarge.copy(
//                                                fontWeight = FontWeight.SemiBold,
//                                                fontSize = rSp(20.sp)
//                                            )
//                                        )
//                                        Spacer(Modifier.height(rDP(6.dp)))
//                                        SubtleNote("We checked for 10s. Servers might be sleepy—try again later.")
//                                    }
//                                } else {
//                                    Column(Modifier.padding(rDP(16.dp))) {
//                                        Text(
//                                            "You're up to date",
//                                            style = MaterialTheme.typography.titleLarge.copy(
//                                                fontWeight = FontWeight.SemiBold,
//                                                color = Mint,
//                                                fontSize = rSp(20.sp)
//                                            )
//                                        )
//                                        Spacer(Modifier.height(rDP(6.dp)))
//                                        MarkdownText("Current version: **${BuildConfig.VERSION_NAME}**")
//                                    }
//                                }
//                            }
//
//                            UpdateStatus.DOWNLOADING -> {
//                                Column(Modifier.padding(rDP(16.dp))) {
//                                    Text(
//                                        "Downloading update…",
//                                        style = MaterialTheme.typography.titleLarge.copy(
//                                            fontSize = rSp(20.sp)
//                                        )
//                                    )
//                                    Spacer(Modifier.height(rDP(8.dp)))
//
//                                    val animatedProgress by animateFloatAsState(
//                                        targetValue = updateInfo.downloadProgress.coerceIn(0f, 1f),
//                                        animationSpec = tween(350),
//                                        label = "ProgressAnim"
//                                    )
//
//                                    if (animatedProgress > 0f) {
//                                        LinearProgressIndicator(
//                                            progress = { animatedProgress },
//                                            modifier = Modifier.fillMaxWidth(),
//                                            color = MaterialTheme.colorScheme.primary,
//                                            strokeCap = StrokeCap.Round
//                                        )
//                                    } else {
//                                        LinearProgressIndicator(
//                                            modifier = Modifier.fillMaxWidth(),
//                                            strokeCap = StrokeCap.Round
//                                        )
//                                    }
//
//                                    if (updateInfo.whatsNew.isNotEmpty()) {
//                                        Spacer(Modifier.height(rDP(12.dp)))
//                                        Text(
//                                            buildAnnotatedString {
//                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
//                                                    append("What's New:\n")
//                                                }
//                                                updateInfo.whatsNew.forEach { append("• $it\n") }
//                                            },
//                                            fontSize = rSp(14.sp)
//                                        )
//                                    }
//                                }
//                            }
//
//                            UpdateStatus.FAILED -> {
//                                Column(Modifier.padding(rDP(16.dp))) {
//                                    Text(
//                                        "Download failed",
//                                        style = MaterialTheme.typography.titleLarge.copy(
//                                            color = MaterialTheme.colorScheme.error,
//                                            fontSize = rSp(20.sp)
//                                        )
//                                    )
//                                    Spacer(Modifier.height(rDP(6.dp)))
//                                    SubtleNote("Network gremlins? Tap **Retry** to try again.")
//                                }
//                            }
//
//                            UpdateStatus.READY_TO_INSTALL -> {
//                                Column(Modifier.padding(rDP(16.dp))) {
//                                    Text(
//                                        "Ready to install",
//                                        style = MaterialTheme.typography.titleLarge.copy(
//                                            fontWeight = FontWeight.SemiBold,
//                                            fontSize = rSp(20.sp)
//                                        )
//                                    )
//                                    if (updateInfo.whatsNew.isNotEmpty()) {
//                                        Spacer(Modifier.height(rDP(8.dp)))
//                                        Text(
//                                            buildAnnotatedString {
//                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
//                                                    append("What's New:\n")
//                                                }
//                                                updateInfo.whatsNew.forEach { append("• $it\n") }
//                                            },
//                                            fontSize = rSp(14.sp)
//                                        )
//                                    }
//                                    Spacer(Modifier.height(rDP(6.dp)))
//                                    SubtleNote("Tap **Install** to finish the upgrade.")
//                                }
//                            }
//                        }
//                    }
//                }
//            }
        }
    }
}


@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier.padding(vertical = rDP(8.dp)),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontSize = rSp(20.sp)
            )
        )
        Spacer(Modifier.height(rDP(4.dp)))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(rDP(6.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label : ${"%.1f".format(value)}",
                fontSize = rSp(14.sp)
            )
            Text(
                "${range.start} – ${range.endInclusive}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = rSp(12.sp)
                )
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SubtleNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = rSp(13.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    )
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(rDP(18.dp)),
    onAction: (() -> Unit)? = null,
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
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = rSp(16.sp)
                )
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) { Text(actionLabel, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(rDP(18.dp)),
    onAction: (() -> Unit)? = null,
    showCard: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = { }
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
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = rSp(16.sp)
                )
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                ) { Text(actionLabel, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Spacer(Modifier.height(rDP(12.dp)))
        AnimatedVisibility(
            visible = showCard,
            enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 4 }) + fadeOut()
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

