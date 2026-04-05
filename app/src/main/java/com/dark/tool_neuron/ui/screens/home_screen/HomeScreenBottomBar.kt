package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.navigation.NavHostController
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionButtonNormalSize
import com.dark.tool_neuron.ui.components.ModeToggleSwitch
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreenBottomBar(navController: NavHostController) {
    val dimens = LocalDimens.current
    var text by remember { mutableStateOf("") }
    var isImageMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    .compositeOver(MaterialTheme.colorScheme.background)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = dimens.spacingMd)
                .padding(top = dimens.spacingXxs, bottom = dimens.spacingSm)
        ) {
            TnTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Say Anything…",
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeToggleSwitch(
                    isImageMode = isImageMode,
                    onModeChange = { isImageMode = it },
                    textModelLoaded = true,
                    imageModelLoaded = true
                )
                Spacer(Modifier.weight(1f))
                ActionButtonNormalSize(
                    onClickListener = {},
                    icon = TnIcons.Send,
                    shape = MaterialShapes.Cookie4Sided.toShape()
                )
            }
        }
    }
}
