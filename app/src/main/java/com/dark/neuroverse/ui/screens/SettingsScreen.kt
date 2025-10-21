package com.dark.neuroverse.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.R
import com.dark.neuroverse.activity.UserDataActivity
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.ChatList
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.userdata.getDefaultChatHistory
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.readBrainFile
import com.dark.neuroverse.userdata.saveTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit = {}) {
    // UI State
    var isLoading by remember { mutableStateOf(true) }
    var chatList by remember { mutableStateOf<List<ChatList>>(emptyList()) }
    var clearingData by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentModel by ModelManager.currentModel.collectAsStateWithLifecycle()
    var selectedVoiceId by remember { mutableIntStateOf(0) }
    selectedVoiceId = UserPrefs.getTTSVoiceId(context).collectAsStateWithLifecycle(0).value

    // Load initial data
    LaunchedEffect(Unit) {
        scope.launch {
            try {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                Text(
                    "Settings", style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }, navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
            )
        }, containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
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
                    )
                }

                // TTS Voice Section
                item {
                    TTSVoiceSection(
                        selectedVoiceId = selectedVoiceId, onVoiceSelected = { voiceId ->
                            selectedVoiceId = voiceId
                            CoroutineScope(Dispatchers.IO).launch {
                                UserPrefs.setTTSVoiceId(context, voiceId)
                            }
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
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(rDP(16.dp)))
        Text(
            "Loading settings...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
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
        ), shape = RoundedCornerShape(rDP(16.dp))
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
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onDismiss, colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun ModelSettingsSection(
    currentModel: ModelData,
) {
    SectionHeader("Model Settings")

    // Current Model Info
    SettingCard(
        title = "Current Model"
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
}

@Composable
private fun ModelInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label, style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ), color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
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
                            modifier = Modifier.size(rDP(16.dp)),
                            strokeWidth = rDP(2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text(
                            "Clearing data...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = rSp(20.sp), fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDP(8.dp))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TTSVoiceSection(
    selectedVoiceId: Int, onVoiceSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    var currentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }

    val voices = TTSVoiceOption.entries
    val initialPage = voices.indexOfFirst { it.id == selectedVoiceId }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage, pageCount = { voices.size })

    // Stop and play audio when page changes
    LaunchedEffect(pagerState.currentPage) {
        // Skip audio on initial load
        if (isInitialLoad) {
            isInitialLoad = false
            onVoiceSelected(voices[pagerState.currentPage].id)
            return@LaunchedEffect
        }

        val currentVoice = voices[pagerState.currentPage]

        // Stop previous audio
        currentMediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        currentMediaPlayer = null

        // Small delay for smooth transition
        delay(100)

        // Play new audio
        try {
            val mediaPlayer = MediaPlayer.create(context, currentVoice.resourceId)
            mediaPlayer?.apply {
                setOnCompletionListener {
                    it.release()
                }
                start()
                currentMediaPlayer = this
            }

            // Update selected voice
            onVoiceSelected(currentVoice.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            currentMediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
    }

    SectionHeader("Text-to-Speech Voice")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
    ) {
        // Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = rDP(60.dp)),
            pageSpacing = rDP(16.dp)
        ) { page ->
            VoiceCard(
                voice = voices[page], isSelected = page == pagerState.currentPage
            )
        }

        // Page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rDP(8.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(voices.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = rDP(4.dp))
                        .size(
                            if (index == pagerState.currentPage) rDP(8.dp) else rDP(6.dp)
                        )
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                )
            }
        }

        // Voice name and navigation hint
        Text(
            text = voices[pagerState.currentPage].displayName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold, fontSize = rSp(20.sp)
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Swipe to explore voices",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VoiceCard(
    voice: TTSVoiceOption, isSelected: Boolean
) {
    Card(
        modifier = Modifier
            .width(rDP(200.dp))
            .height(rDP(200.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) rDP(8.dp) else rDP(2.dp)
        ),
        shape = RoundedCornerShape(rDP(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(rDP(20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = voice.displayName, style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = rSp(24.sp)
                ), color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }, textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(rDP(12.dp)))

            Text(
                text = voice.description, style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(14.sp)
                ), color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }, textAlign = TextAlign.Center
            )
        }
    }
}

enum class TTSVoiceOption(
    val id: Int, val displayName: String, val description: String, val resourceId: Int
) {
    AF(0, "AF", "Female - Standard", R.raw.af), AF_BELLA(
        1, "Bella", "Female - Warm", R.raw.af_bella
    ),
    AF_NICOLE(2, "Nicole", "Female - Professional", R.raw.af_nicole), AF_SARAH(
        3, "Sarah", "Female - Clear", R.raw.af_sarah
    ),
    AF_SKY(4, "Sky", "Female - Energetic", R.raw.af_sky), AM_ADAM(
        5, "Adam", "Male - Standard", R.raw.am_adam
    ),
    AM_MICHAEL(6, "Michael", "Male - Professional", R.raw.am_michael), BF_EMMA(
        7, "Emma", "British Female - Elegant", R.raw.bf_emma
    ),
    BF_ISABELLA(8, "Isabella", "British Female - Refined", R.raw.bf_isabella), BM_GEORGE(
        9, "George", "British Male - Distinguished", R.raw.bm_george
    ),
    BM_LEWIS(10, "Lewis", "British Male - Clear", R.raw.bm_lewis)
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(rDP(18.dp)),
    onAction: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(roundedCornerShape),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shape = roundedCornerShape
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = rDP(16.dp), vertical = rDP(12.dp))
                .animateContentSize(animationSpec = tween(250))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title, style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = rSp(16.sp), fontWeight = FontWeight.SemiBold
                    ), color = MaterialTheme.colorScheme.onSurface
                )
                if (actionLabel != null && onAction != null) {
                    Button(
                        onClick = onAction, colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(
                            text = actionLabel, style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            content?.let {
                Spacer(Modifier.height(rDP(12.dp)))
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ), shape = RoundedCornerShape(rDP(12.dp))
                ) {
                    it()
                }
            }
        }
    }
}

// Helper functions moved outside composable scope
private suspend fun loadChatHistory(context: Context): List<ChatList> =
    withContext(Dispatchers.IO) {
        try {
            val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            val rootNode = readBrainFile(key, context)
            val root = rootNode.getNodeDirect("root")
            val history = getDefaultChatHistory(root)

            val chatInfo = mutableListOf<ChatList>()
            NeuronTree(history).getAllChildrenRecursive().forEach { node ->
                if (node.data.content.isNotBlank()) {
                    val title = runCatching {
                        JSONObject(node.data.content).optString("title", "Untitled")
                    }.getOrElse { "Untitled" }
                    chatInfo.add(ChatList(node.id, title))
                }
            }
            chatInfo
        } catch (e: Exception) {
            Log.e("loadChatHistory", "Failed to load chat history", e)
            emptyList()
        }
    }

private suspend fun clearChatHistory(context: Context, chatList: List<ChatList>) =
    withContext(Dispatchers.IO) {
        val key = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
        val rootNode = readBrainFile(key, context)

        chatList.forEach { chat ->
            rootNode.deleteNodeById(chat.id)
        }

        saveTree(rootNode, context, BuildConfig.ALIAS)
        Log.d("clearChatHistory", "Cleared ${chatList.size} chats")
    }

private suspend fun playVoiceSample(context: Context, voiceId: Int) =
    withContext(Dispatchers.Main) {
        val voice = TTSVoiceOption.entries.find { it.id == voiceId } ?: return@withContext

        val mediaPlayer = MediaPlayer.create(context, voice.resourceId)
        mediaPlayer?.apply {
            setOnCompletionListener {
                it.release()
            }
            start()
        }
    }