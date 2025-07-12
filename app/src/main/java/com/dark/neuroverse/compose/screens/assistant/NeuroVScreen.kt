package com.dark.neuroverse.compose.screens.assistant

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.ai_manager.ai.data.db.DatabaseProvider
import com.dark.ai_manager.ai.local.Neuron
import com.dark.mylibrary.STTManager
import com.dark.neuroverse.R
import com.dark.neuroverse.compose.components.GlitchTypingText
import com.dark.neuroverse.compose.components.ShimmerText
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.ui.theme.Success
import com.dark.neuroverse.utils.UserPrefs
import com.dark.neuroverse.utils.taskRouterSystemPrompt
import com.dark.neuroverse.viewModel.NeuroVScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File


private val cardColor = Color(0xFFEFEFEF)

@Composable
fun NeuroVScreen(onClickOutside: () -> Unit) {
    val viewModel = remember { NeuroVScreenViewModel() }
    var action by remember { mutableStateOf(Action.NONE) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = DatabaseProvider.getDatabase(context)
            Neuron.loadModel(
                File(db.ModelDAO().getAllModels().first()[0].modelPath),
                systemPrompt = taskRouterSystemPrompt
            )
        }
    }

    NeuroVerseTheme {
        Column(
            modifier = ComposeConfig.NeuroVScreen_rootModifier.clickable {
                onClickOutside()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Column(
                modifier = ComposeConfig.NeuroVScreen_holderModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeuroVHeader(action, onBack = { action = Action.NONE })
                NeuroVBody(action, onClick = { action = it }, viewModel)
                NeuroVBottomBar(action, viewModel, plg = { })
            }
        }
    }
}

@Composable
internal fun NeuroVHeader(action: Action, onBack: () -> Unit = {}) {
    Row(
        ComposeConfig.NeuroVHeader_rootModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        AnimatedVisibility(action != Action.NONE) {
            Icon(
                painterResource(R.drawable.back),
                contentDescription = "Send",
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
            )
        }

        Spacer(Modifier.width(20.dp))

        Text(
            "Neuro V",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1f)
        )

        IconButton(
            onClick = {
                //Open Settings Activity
            },
            modifier = Modifier.padding(end = 16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.settings), contentDescription = "Settings"
            )
        }
    }
}

@Composable
internal fun NeuroVBody(
    action: Action,
    onClick: (action: Action) -> Unit,
    viewModel: NeuroVScreenViewModel
) {
    AnimatedContent(action, transitionSpec = {
        (fadeIn()).togetherWith(fadeOut())
    }, label = "Action") {
        when (it) {
            Action.NONE -> ComposeComponents.BodyContentNone(onClick)

            Action.SPEAK -> {
                val context = LocalContext.current
                val stt = remember(context) { STTManager(context) }

                Column {
                    ComposeComponents.BodyContentSTT(stt)
                    ComposeComponents.BottomBarActionSpeak(stt)
                }
            }

            else -> ComposeComponents.ResultComposable(viewModel, action)
        }
    }
}

@Composable
internal fun NeuroVBottomBar(action: Action, viewModel: NeuroVScreenViewModel, plg: () -> Unit) {
    Row(
        ComposeConfig.NeuroVBottomBar_rootModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBox(action, viewModel, plg)
    }
}

@Composable
internal fun BottomNavButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.Black else Color.White)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color.Black,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}

@Composable
internal fun ActionBox(
    action: Action,
    viewModel: NeuroVScreenViewModel,
    onPluginSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(action, transitionSpec = {
            (fadeIn()).togetherWith(fadeOut())
        }, label = "Action") {
            when (it) {
                Action.WRITE -> ComposeComponents.BottomBarActionWrite(viewModel)

                Action.SPEAK -> {

                }

                else -> ComposeComponents.DefaultActionCompose(onPluginSelected)
            }
        }
    }
}

internal object ComposeConfig {

    val NeuroVScreen_rootModifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
        .padding(bottom = 34.dp)

    val NeuroVScreen_holderModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White)
        .padding(20.dp)


    val NeuroVHeader_rootModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp, 12.dp, 6.dp, 6.dp))
        .background(cardColor)


    val NeuroVBottomBar_rootModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp, 6.dp, 12.dp, 12.dp))
        .background(cardColor)

}

internal object ComposeComponents {

    @Composable
    internal fun QuickActionCard(
        modifier: Modifier = Modifier,
        icon: Painter,
        title: String,
        desc: String,
        isCheckable: Boolean = false,
        onClick: () -> Unit = {}
    ) {
        var checked by remember { mutableStateOf(false) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            UserPrefs.isAGU(context).collect {
                checked = it
            }
        }

        LaunchedEffect(checked) {
            UserPrefs.setAGU(context, checked)
        }

        val animColor = animateColorAsState(
            if (checked) Color(0xFF0FB100) else Color.Black, animationSpec = tween(
                durationMillis = 500, easing = FastOutSlowInEasing
            )
        )

        val highlightColor = if (isCheckable) animColor.value else Color.Black

        Card(
            modifier,
            shape = RoundedCornerShape(6.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(10.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable {
                            if (isCheckable) {
                                checked = !checked
                            } else {
                                Log.d("QuickActionCard", "Clicked")
                                onClick()
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier.size(84.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = icon,
                            contentDescription = title,
                            tint = highlightColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (highlightColor != Color.Unspecified) highlightColor else Color.Black,
                                fontWeight = FontWeight.SemiBold
                            ),
                        )
                    }
                }

                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(),
                    color = Color.DarkGray
                )
            }
        }
    }

    @Composable
    fun BodyContentNone(onClick: (action: Action) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.typing),
                title = "Write To AI",
                desc = "Feel to be Private..? Try Typing Your Task To AI....",
                onClick = {
                    onClick(Action.WRITE)
                }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.mic),
                title = "Speak..!",
                desc = "No Need To Type, Just Click And Let the Magic Happen",
                onClick = {
                    onClick(Action.SPEAK)
                }
            )
//            QuickActionCard(
//                modifier = Modifier.weight(1f),
//                icon = painterResource(R.drawable.brain), // swap for your AGU icon
//                title = "AGU",
//                desc = "Let the AI understand the surrounding & make decisions",
//                true
//            )
        }
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun BodyContentSTT(stt: STTManager) {
        val bufferResults by stt.bufferSpeechResults.collectAsState()
        var finalResults by remember { mutableStateOf("") }
        val isListening by stt.isListening.collectAsState()

        val context = LocalContext.current
        rememberCoroutineScope()

        var lastSpeechTimestamp by remember { mutableLongStateOf(0L) }
        var speechOngoing by remember { mutableStateOf(false) }
        var output by remember { mutableStateOf("") }
        if (isListening) output = ""

        // Track buffer for speech activity
        LaunchedEffect(bufferResults) {
            if (bufferResults.isNotBlank()) {
                finalResults += bufferResults
                Log.d("SpeechOutput", "Buffer text is: $bufferResults")
                lastSpeechTimestamp = System.currentTimeMillis()
                if (!speechOngoing) {
                    speechOngoing = true
                    Toast.makeText(context, "Speech Resumed (Interrupt)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        LaunchedEffect(finalResults) {
            if (finalResults.isNotBlank()) {
                Toast.makeText(context, "Final Speech Text: $finalResults", Toast.LENGTH_SHORT)
                    .show()

                // You can also trigger your model, save text, or whatever:
                Log.d("SpeechOutput", "Final text is: $finalResults")
            }
        }


        // Pause detection with proper coroutine
        LaunchedEffect(isListening) {
            if (isListening) {
                speechOngoing = false
                lastSpeechTimestamp = System.currentTimeMillis()

                while (true) {
                    kotlinx.coroutines.delay(500)
                    val timeSinceLastSpeech = System.currentTimeMillis() - lastSpeechTimestamp

                    if (speechOngoing && timeSinceLastSpeech > 2000) {
                        speechOngoing = false
                        Toast.makeText(context, "Speech Paused (End Segment)", Toast.LENGTH_SHORT)
                            .show()
                        stt.stop()
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                }
            } else {
                if (finalResults.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        Neuron.updateSystemPrompt("YOU ARE A HELP FULL AI ASSISTANT AND YOU REPLY FOR EVERY RESPONSE IN A VERY POLITE MANNER")

                        val temp = """
                            YOU ARE A HELP FULL AI ASSISTANT AND YOU REPLY FOR EVERY RESPONSE IN A VERY POLITE MANNER
                            
                            here is the user prompt
                            $finalResults
                        """.trimIndent()

                        output = Neuron.generateResponseStreaming(temp) {
                            output += it
                        }
                    }
                }
                if (output.isNotEmpty()) {
                    Text(output)
                    finalResults = ""
                } else {
                    Text("Tap mic below to start speaking", color = Color.Gray)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ResultComposable(viewModel: NeuroVScreenViewModel, action: Action) {
        val scrollState = rememberScrollState()

        when (action) {
            Action.TASKS -> {}

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 450.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardColor)
                        .verticalScroll(scrollState)
                ) {
                    val typeText = viewModel.result.collectAsState().value.optString("type", "")
                    val resultText = viewModel.result.collectAsState().value.optString("result", "")
                    val isGenerating = viewModel.isGenerating.collectAsState().value

                    AnimatedVisibility(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        visible = isGenerating,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LoadingIndicator()
                                ShimmerText(text = "Generating...")
                            }
                        }
                    }

                    AnimatedVisibility(
                        modifier = Modifier
                            .fillMaxWidth(),
                        visible = !isGenerating && resultText.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        when (typeText) {
                            "wiki_search" -> ResultCompose.SearchResultComposable(
                                JSONObject(
                                    resultText
                                )
                            )

                            else -> {
                                GlitchTypingText(
                                    finalText = resultText,
                                    delayPerChar = 1L,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun BottomBarActionWrite(viewModel: NeuroVScreenViewModel) {
        var text by remember { mutableStateOf("Search Online For What is Brain ?") }
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var isSearchOnline by remember { mutableStateOf(false) }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val stt = remember(context) { STTManager(context) }
            val speechResults by stt.speechResults.collectAsState()
            val isListening by stt.isListening.collectAsState()

            LaunchedEffect(speechResults) {
                if (speechResults.isBlank()) return@LaunchedEffect
                val result = JSONObject(speechResults)
                text = result.getString("text")
            }

            BasicTextField(
                modifier = Modifier.weight(1f),
                value = text,
                onValueChange = { text = it },
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "Write Message Here...",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Icon(
                painterResource(R.drawable.web_search),
                contentDescription = "Search",
                tint = if (isSearchOnline) Success else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    isSearchOnline = !isSearchOnline
                })

            // SPEAK
            when (isListening) {
                true -> {
                    LoadingIndicator(Modifier.clickable {
                        stt.stop()
                    })
                }

                false -> {
                    Icon(
                        painterResource(R.drawable.mic),
                        contentDescription = "Speak",
                        modifier = Modifier.clickable {
                            if (stt.isModelReady()) {
                                stt.startListening()
                            } else {
                                Toast.makeText(context, "Model loading...", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    )
                }
            }

            //SEND
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Send",
                modifier = Modifier.clickable {
                    if (text.isNotBlank()) {
                        val safePrompt = text
                        text = ""

                        coroutineScope.launch(Dispatchers.IO) {
                            viewModel.processUserPrompt(isSearchOnline, safePrompt)
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun BottomBarActionSpeak(stt: STTManager) {
        val context = LocalContext.current
        val isListening by stt.isListening.collectAsState()

        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    if (stt.isModelReady()) {
                        if (isListening) stt.stop() else stt.startListening()
                    } else {
                        Toast.makeText(context, "Model loading...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(54.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    contentDescription = "Speak"
                )
            }
        }
    }

    @Composable
    fun DefaultActionCompose(onPluginSelected: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(cardColor), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Plugins Actions",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            VerticalDivider(modifier = Modifier.height(50.dp), thickness = 2.dp)

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 10.dp)
            ) {
                items(4) { it ->
                    BottomNavButton(it.toString(), selected = false) {
                        onPluginSelected()
                    }
                }
            }
        }
    }

    internal object ResultCompose {
        @Composable
        fun SearchResultComposable(
            content: JSONObject
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Bar Title
                Text(
                    text = "Search Results",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Main Title
                Text(
                    text = content.getString("title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Abstract Summary
                Text(
                    text = content.getString("summary"),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color.Black.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(24.dp))


                Column {
                    if (content.has("related_items")) {
                        val jsonArray = content.getJSONArray("related_items")
                        val relatedList = List(jsonArray.length()) { index ->
                            val jsonObject = jsonArray.getJSONObject(index)
                            RelatedTopics(
                                jsonObject.getString("text"),
                                jsonObject.getString("url")
                            )
                        }

                        // Related Topics
                        Text(
                            text = "Related Topics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )

                        relatedList.forEach {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Related Topic")
                                Text(text = it.text, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }


        internal data class RelatedTopics(
            val text: String,
            val url: String
        )
    }
}

internal enum class Action {
    NONE, WRITE, SPEAK, TASKS
}