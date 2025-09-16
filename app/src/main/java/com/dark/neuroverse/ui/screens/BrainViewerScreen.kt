@file:OptIn(ExperimentalMaterial3Api::class)

package com.dark.neuroverse.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.rDP

@SuppressLint("NewApi")
@Preview
@Composable
fun BrainViewerScreen() {

    var isSecure by remember { mutableStateOf(false) }

    val secureColor = animateColorAsState(
        if (isSecure) Mint else Coral, animationSpec = tween(durationMillis = 700)
    )

    NeuroVerseTheme {
        Scaffold(
            topBar = {
                TopBar(secureColor = secureColor.value)
            }) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding), contentAlignment = Alignment.Center
            ) {
                Column(Modifier.fillMaxSize()) {
                    QuickLook()
                    DetailedLook(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TopBar(secureColor: Color) {
    CenterAlignedTopAppBar(title = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.privicy),
                contentDescription = "Privacy Icon",
            )
            Text(
                text = "Brain Viewer", style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                )
            )
        }

    }, actions = {
        Box(
            Modifier
                .padding(end = rDP(24.dp))
                .size(rDP(8.dp))
                .background(secureColor, CircleShape)
        )
    })
}

@Composable
fun DetailedLook(modifier: Modifier = Modifier) {

    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(18.dp))
            .padding(bottom = rDP(18.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(rDP(8.dp))
        ) {
            Text(
                "Quick Looks",
                modifier = Modifier.padding(rDP(8.dp)),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                )
            )

            QuickChipsWithChild(
                Modifier, "Root", Coral.copy(alpha = 0.1f), childComposable = {
                    QuickChipsWithChild(
                        Modifier.padding(start = rDP(14.dp)), "Chat", Coral.copy(alpha = 0.1f)
                    ) {
                        QuickChips(
                            Modifier.padding(start = rDP(34.dp)),
                            "Hi Bro",
                            Coral.copy(alpha = 0.1f)
                        ) {

                        }
                    }
                })
        }
    }
}

@Composable
fun QuickLook() {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(18.dp))
            .padding(vertical = rDP(18.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(rDP(8.dp)), verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            Text(
                "Quick Looks",
                modifier = Modifier.padding(rDP(8.dp)),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                )
            )

            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChips(Modifier.weight(1f), Pair("Chats", ""))
                QuickChips(Modifier.weight(1f), Pair("Messages", ""))
            }

            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChips(Modifier.weight(1f), Pair("Users", ""))
                QuickChips(Modifier.weight(1f), Pair("Assistants", ""))
            }

            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChips(Modifier.weight(1f), Pair("Tool-Call", ""))
            }
        }
    }
}

@Composable
fun QuickChips(
    modifier: Modifier = Modifier,
    data: Pair<String, String> = Pair("", ""),
    backgroundColor: Color = SkyBlue.copy(alpha = 0.1f)
) {
    Row(
        modifier
            .background(
                backgroundColor, RoundedCornerShape(rDP(8.dp))
            )
            .padding(rDP(8.dp))
    ) {
        Text(
            data.first, style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            data.second, style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
            )
        )
    }
}

@Composable
fun QuickChips(
    modifier: Modifier = Modifier,
    data: String = "",
    backgroundColor: Color = Coral.copy(alpha = 0.1f),
    tailAction: @Composable () -> Unit = {}
) {
    Row(
        modifier
            .background(
                backgroundColor, RoundedCornerShape(rDP(8.dp))
            )
            .padding(rDP(8.dp))
    ) {
        Text(
            data, style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        tailAction()
    }
}

@Composable
fun QuickChipsWithChild(
    modifier: Modifier = Modifier,
    data: String = "",
    backgroundColor: Color = SkyBlue.copy(alpha = 0.1f),
    childComposable: @Composable () -> Unit = {},
) {
    var showChats by remember { mutableStateOf(true) }

    Column {
        Row(
            modifier
                .background(
                    backgroundColor, RoundedCornerShape(rDP(8.dp))
                )
                .padding(rDP(8.dp))
        ) {
            Text(
                data, style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(Modifier.clickable {
                showChats = !showChats
            }) {
                Crossfade(showChats) {
                    when (it) {
                        true -> {
                            Icon(Icons.Default.KeyboardArrowDown, "")
                        }

                        false -> {
                            Icon(Icons.Default.KeyboardArrowUp, "")
                        }
                    }
                }
            }
        }
        AnimatedVisibility(showChats) {
            Column(
                Modifier.padding(vertical = rDP(8.dp)), verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                childComposable()
            }

        }
    }

}