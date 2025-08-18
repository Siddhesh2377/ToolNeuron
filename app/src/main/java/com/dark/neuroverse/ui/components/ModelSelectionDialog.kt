package com.dark.neuroverse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.ai_module.model.ModelsData

@Composable
fun ModelDialog(
    modelList: List<ModelsData>, onDismissRequest: (ModelsData?) -> Unit
) {
    Dialog(
        onDismissRequest = { onDismissRequest(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false) // full-width on small screens
    ) {
        AnimatedVisibility(
            visible = true, enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                initialScale = 0.9f, animationSpec = tween(300)
            ), exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                targetScale = 0.9f, animationSpec = tween(200)
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .wrapContentHeight()
                ) {
                    // Title
                    Text(
                        text = "Select Model",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Scrollable model list
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        modelList.forEachIndexed { index, model ->
                            Button(
                                onClick = {
                                    onDismissRequest(model)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    text = model.modeName,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            if (index != modelList.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel button
                    TextButton(
                        onClick = { onDismissRequest(null) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}