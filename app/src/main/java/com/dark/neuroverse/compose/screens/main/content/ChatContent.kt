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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.dark.neuroverse.utils.vibrate
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

            Icon(Icons.AutoMirrored.Default.ArrowBack, "back", modifier = Modifier.size(38.dp).clickable {
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

@Composable
fun BodyChat(modifier: Modifier) {

    Card(
        modifier = modifier.padding(bottom = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(
            topEnd = 24.dp,
            topStart = 24.dp,
            bottomEnd = 8.dp,
            bottomStart = 8.dp
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomChat() {
    val context = LocalContext.current
    val text by UserInput.text.collectAsState()
    var startAudio by remember { mutableStateOf(false) }


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

                    }, colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AnimatedContent(text.isNotEmpty(), label = "Audio Animated") { it ->

                        when (it) {
                            true -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.TwoTone.Send,
                                    contentDescription = "Audio"
                                )
                            }

                            false -> {
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


object UserInput {
    private val _textState = MutableStateFlow("Initial text")
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