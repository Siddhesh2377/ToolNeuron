package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.state.getDisplayText
import com.dark.tool_neuron.models.state.getIcon
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.theme.rDp

@Composable
fun AnimatedTitle(modifier: Modifier = Modifier, onShowDynamicWindow: () -> Unit = {}) {
    val appState by AppStateManager.appState.collectAsState()

    AnimatedContent(
        targetState = appState,
        transitionSpec = {
            fadeIn(animationSpec = tween(520)) togetherWith
                    fadeOut(animationSpec = tween(520))
        },
        label = "AppStateTitleAnim"
    ) { state ->
        TitleRow(
            text = state.getDisplayText(),
            icon = state.getIcon(),
            state = state,
            modifier = modifier.clickable{
                onShowDynamicWindow()
            }
        )
    }
}


@Composable
fun ModelListItem(
    modifier: Modifier, model: Model, isLoaded: Boolean, onClickListener: (model: Model) -> Unit
) {
    Card(
        modifier, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.04f)
        )
    ) {
        Row(
            modifier = Modifier.padding(rDp(8.dp)), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(model.modelName)
            Spacer(Modifier.weight(1f))
            Crossfade(isLoaded) {
                when (it) {
                    true -> {
                        ActionTextButton(onClickListener = {
                            onClickListener(model)
                        }, Icons.Default.SubdirectoryArrowLeft, text = "Unload")
                    }

                    false -> {
                        ActionButton(onClickListener = {
                            onClickListener(model)
                        }, Icons.Default.ArrowOutward)
                    }
                }
            }
        }
    }
}