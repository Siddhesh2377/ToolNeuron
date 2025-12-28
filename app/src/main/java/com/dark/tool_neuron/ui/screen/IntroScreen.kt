package com.dark.tool_neuron.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.rDp
import kotlinx.coroutines.delay

@Composable
fun IntroScreen() {

    var progress by remember { mutableFloatStateOf(.5f) }
    var delay by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {

        delay = 8

        delay(2000)

        delay = 3

        delay(2000)

        delay = 8
    }

    LaunchedEffect(Unit) {
        for (i in 1..1000) {
            delay(delay)
            progress = i / 1000f
        }
    }

    Scaffold(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            Column(
                Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painterResource(R.drawable.ai_model),
                    contentDescription = "App-Icon",
                    Modifier.size(rDp(70.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Tool-Neuron",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    fontFamily = FontFamily.SansSerif
                )

                Text(
                    "Where Your Privacy Matters :)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.SansSerif
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = rDp(20.dp))
                    .height(rDp(6.dp))
            )
        }
    }
}
