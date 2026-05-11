package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.Motion
import kotlinx.coroutines.delay

private val PARAGRAPHS = listOf(
    "ToolNeuron runs on your phone. The models, your chats, attached documents, voice clips, and every setting stay on this device. Nothing is sent to me, nothing is sent to a server I run, and there is no analytics SDK collecting how you use the app.",
    "You bring your own models. The app loads files you download from places like HuggingFace or import from local storage. What a model says is on the model, not on me. Treat its replies the way you would treat anything off the open internet. Verify before you act on it.",
    "I cannot promise the output is accurate, safe, or legal in your context. If you use the app for medical, legal, financial, or safety decisions, that is your call and your risk. Do not paste anything into a model that you would not paste into a public notepad you do not fully control.",
    "The Remote Server feature opens a port on your local network so other devices can talk to the loaded model over plain HTTP. There is no TLS in this build. Only run it on networks you trust, and turn it off when you are done. Anyone on the same Wi-Fi who guesses or steals your bearer token can use the model from your phone.",
    "Some features rely on the public internet when you choose to use them. Research fetches pages from search engines and websites. The HuggingFace Explorer talks to HuggingFace. These calls go straight from your phone to those services and follow whatever those services log on their side.",
    "If you lose your PIN, the data goes with it. There is no recovery. The panic PIN wipes everything on purpose. The full reset in Settings does the same thing. Use them only when you mean it.",
    "By tapping the button below you agree to use the app under these terms. If you do not agree, close the app and remove it from your phone. You can read these terms again later from Settings.",
)

@Composable
fun TermsConditionsScreen(
    innerPadding: PaddingValues,
    onAccept: () -> Unit,
) {
    val dimens = LocalDimens.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    BackHandler(enabled = true) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
    ) {
        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 },
        ) {
            Column {
                Icon(
                    imageVector = TnIcons.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(dimens.spacingLg))
                Text(
                    text = "A few things to read first",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(dimens.spacingXs))
                Text(
                    text = "Plain English. Not lawyer English.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 },
        ) {
            Column {
                PARAGRAPHS.forEachIndexed { index, paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (index != PARAGRAPHS.lastIndex) {
                        Spacer(Modifier.height(dimens.spacingMd))
                    }
                }
            }
        }

        Spacer(Modifier.height(dimens.spacingXl))
    }
}
