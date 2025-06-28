package com.dark.neuroverse.compose.screens.main.content

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.R
import com.dark.neuroverse.compose.components.RichText
import com.dark.neuroverse.neurov.mcp.chat.viewModels.ChattingViewModel
import com.dark.neuroverse.utils.vibrate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HeaderChat(onBack: () -> Unit) {
    var deleteButton by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 20.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                "back",
                modifier = Modifier
                    .size(38.dp)
                    .clickable {
                        onBack()
                    })

            Spacer(Modifier.width(10.dp))

            Text(
                "NeuroV Chat",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            if (deleteButton) {
                IconButton(
                    onClick = {

                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        "settings",
                        modifier = Modifier
                            .size(26.dp)
                    )
                }
            }

        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RichText(
                "Privacy Focused **Offline AI**\n" +
                        "On Your Device",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light
            )

            Icon(painterResource(R.drawable.shield), "protected data")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BodyChat(
    modifier: Modifier,
    viewModel: ChattingViewModel
) {
    // Scroll state (if you still want scrolling)
    val scrollState = rememberScrollState()

    // Your existing state
    val streamingResponse by viewModel.streamingBuffer.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val latestResponse = viewModel.getLatestAIResponse() ?: ""

    // 1) Animate a “Typing” ellipsis
    var dotCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(isThinking) {
        if (isThinking) {
            // while the AI is generating, bump the dotCount every 500ms
            while (true) {
                delay(500)
                dotCount = (dotCount + 1) % 4       // cycles 0,1,2,3
            }
        }
        // reset once generation stops
        dotCount = 0
    }

    // 2) Build your displayText
    val displayText = if (isThinking) {
        // "Typing", "Typing.", "Typing..", "Typing..."
        "Typing" + ".".repeat(dotCount)
    } else {
        streamingResponse.ifEmpty { latestResponse }
    }

    Card(
        modifier = modifier.padding(bottom = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(24.dp, 24.dp, 8.dp, 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(isThinking) {
                    if (it)
                        LoadingIndicator(Modifier.size(24.dp))
                }
                Spacer(Modifier.width(8.dp))
                RichText(
                    displayText,
                    modifier = Modifier.fillMaxSize(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    // auto-scroll to bottom when text changes
    LaunchedEffect(displayText) {
        scrollState.scrollTo(scrollState.maxValue)
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomChat(viewModel: ChattingViewModel) {
    val context = LocalContext.current
    val text by UserInput.text.collectAsState()
    var startAudio by remember { mutableStateOf(false) }
    val isGenerating by viewModel.isGenerating.collectAsState()


    // Main container (mimics the Card with a pill shape)
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(
                topEnd = 8.dp,
                topStart = 8.dp,
                bottomEnd = 24.dp,
                bottomStart = 24.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { UserInput.updateText(it) },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    "Say Anything...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                IconButton(
                    onClick = {
                        startAudio = !startAudio
                        vibrate(context)
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AnimatedContent(startAudio, label = "Audio Animated") { it ->
                        when (it) {
                            true -> {
                                val (a, r, g, b) = listOf(
                                    Color.White.alpha,
                                    Color.Red.alpha,
                                    Color.Green.alpha,
                                    Color.Blue.alpha
                                )

                                LoadingIndicator(
                                    color = LoadingIndicatorDefaults.containedIndicatorColor.copy(
                                        a,
                                        r,
                                        g,
                                        b
                                    )
                                )
                            }

                            false -> {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Mic"
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (isGenerating) {
                            viewModel.stopGenerating()
                        } else {
                            val text = UserInput.text.value
                            UserInput.updateText("")
                            vibrate(context)
                            viewModel.sendMessage(text)
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AnimatedContent(text.isNotEmpty(), label = "Audio Animated") { isTextNotEmpty ->
                        AnimatedContent(isGenerating) { isGeneratingText ->
                            when {
                                isGeneratingText -> {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularWavyProgressIndicator()
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Mic"
                                        )
                                    }
                                }

                                isTextNotEmpty -> {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.TwoTone.Send,
                                        contentDescription = "Audio"
                                    )
                                }

                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Audio"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


object UserInput {
    private val _textState = MutableStateFlow("Hey Bro..!")
    val text: StateFlow<String> = _textState

    private val _speakState = MutableStateFlow(false)
    val speck: StateFlow<Boolean> = _speakState

    fun updateText(newText: String) {
        _textState.value = newText
    }

    fun updateSpeak(newSpeak: Boolean) {
        _speakState.value = newSpeak
    }
}