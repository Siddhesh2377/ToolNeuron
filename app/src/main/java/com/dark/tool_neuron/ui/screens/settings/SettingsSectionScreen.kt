package com.dark.tool_neuron.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.screens.settings.components.SettingsDialogHost
import com.dark.tool_neuron.ui.screens.settings.components.SettingsItemRow
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsSectionScreen(
    innerPadding: PaddingValues,
    sectionId: String,
    viewModel: SettingsViewModel,
    onNavigate: (route: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current
    val section = state.sections.firstOrNull { it.id == sectionId }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40); visible = true }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { route -> onNavigate(route) }
    }

    if (section == null || section.items.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(dimens.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BodyLabel(text = "Nothing to configure here yet.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                top = innerPadding.calculateTopPadding() + dimens.spacingSm,
                bottom = innerPadding.calculateBottomPadding() + dimens.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            if (!section.description.isNullOrBlank()) {
                item(key = "section-desc") {
                    AnimatedRow(visible = visible, stagger = 0) {
                        CaptionText(
                            text = section.description,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = dimens.spacingXs),
                        )
                    }
                }
            }
            itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                AnimatedRow(visible = visible, stagger = (index + 1) * 40) {
                    SettingsItemRow(
                        item = item,
                        onOpenChoice = { choice -> viewModel.requestChoiceDialog(choice) },
                    )
                }
            }
        }
    }

    SettingsDialogHost(
        dialog = state.dialog,
        onDismiss = viewModel::dismissDialog,
    )
}

@Composable
private fun AnimatedRow(
    visible: Boolean,
    stagger: Int,
    content: @Composable () -> Unit,
) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (stagger > 0) delay(stagger.toLong())
            ready = true
        } else {
            ready = false
        }
    }
    AnimatedVisibility(
        visible = ready,
        enter = fadeIn(Motion.entrance()) +
            slideInVertically(Motion.entrance()) { it / 8 },
    ) {
        content()
    }
}
