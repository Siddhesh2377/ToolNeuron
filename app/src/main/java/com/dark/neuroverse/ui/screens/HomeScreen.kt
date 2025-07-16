package com.dark.neuroverse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.ai.Neuron
import com.dark.neuroverse.R
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.ROLE
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.ChattingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(viewModel: ChattingViewModel = viewModel()) {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                Neuron.generateResponseStreaming("Greet The User") { token ->
                    text += token
                }
            }
        } catch (e: Exception) {
            text = "Error: ${e.message}"
        }
    }

    WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = rDP(12.dp))
            .padding(top = rDP(8.dp))
            .imePadding() // this pushes BottomBar above keyboard
    ) {
        TopBar()
        Conversations(Modifier.weight(1f))
        BottomBar() // Don't apply weight here!
    }
}

@Composable
internal fun TopBar() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        IconButton(onClick = {}) {
            Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
        }

        Text(
            "NeuroV Chat", style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.W100
            )
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = {}) {
            Icon(painter = painterResource(R.drawable.new_chat), contentDescription = "New Chat")
        }

        IconButton(onClick = {}) {
            Icon(painter = painterResource(R.drawable.more), contentDescription = "More")
        }
    }
}

@Composable
internal fun Conversations(modifier: Modifier = Modifier) {
    @Composable
    fun ChatBubble(message: Message) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.role == ROLE.USER) Alignment.End else Alignment.Start
        ) {
            Text(
                text = message.content, style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal
                ), modifier = Modifier
                    .then(
                        if (message.role == ROLE.USER) Modifier.background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.extraLarge
                        ) else Modifier
                    )
                    .padding(vertical = rDP(8.dp), horizontal = rDP(18.dp))
            )

            if (message.role != ROLE.USER) {
                Row(
                    modifier = Modifier.padding(
                        top = rDP(4.dp), start = rDP(18.dp)
                    ), horizontalArrangement = Arrangement.spacedBy(rDP(14.dp))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = "Copy",
                        modifier = Modifier.size(rDP(14.dp))
                    )

                    Icon(
                        painter = painterResource(R.drawable.new_action),
                        contentDescription = "Layers",
                        modifier = Modifier.size(rDP(14.dp))
                    )
                }
            }
        }
    }

    val messages = listOf(
        Message(
            ROLE.USER, "So What Can You Do?", "12:00 PM"
        ), Message(
            ROLE.SYSTEM,
            "I am Ai Assistant, I can search web and work with offline tools for The Device",
            "12:00 PM"
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = rDP(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        items(messages.size) { index ->
            ChatBubble(message = messages[index])
        }
    }
}

@Composable
internal fun BottomBar(modifier: Modifier = Modifier) {

    var text by remember {
        mutableStateOf("")
    }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDP(16.dp))
                .clip(MaterialTheme.shapes.medium)
                .background(color = MaterialTheme.colorScheme.primary)
                .heightIn(max = 400.dp)
                .padding(vertical = rDP(8.dp))
                .padding(start = rDP(12.dp), end = rDP(10.dp)),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = text,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary
                    ),
                    onValueChange = {
                        text = it
                    },
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
                    })
            }


            IconButton(
                modifier = Modifier.size(rDP(26.dp)), onClick = {

                }, colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {

                Icon(
                    painter = painterResource(R.drawable.attachment),
                    contentDescription = "Audio",
                    modifier = Modifier.size(rDP(14.dp))
                )
            }

            IconButton(
                modifier = Modifier.size(rDP(26.dp)), onClick = {

                }, colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {

                Icon(
                    painter = painterResource(R.drawable.send_chat),
                    contentDescription = "Audio",
                    modifier = Modifier.size(rDP(16.dp))
                )
            }
        }
    }
}

