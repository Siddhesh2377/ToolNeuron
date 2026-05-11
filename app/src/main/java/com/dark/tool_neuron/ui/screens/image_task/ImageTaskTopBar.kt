package com.dark.tool_neuron.ui.screens.image_task

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTaskTopBar(onBack: () -> Unit) {
    val dimens = LocalDimens.current
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Image Task",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            ActionButton(
                onClickListener = onBack,
                icon = TnIcons.ArrowLeft,
                contentDescription = "Back",
                modifier = Modifier.padding(start = dimens.screenPadding),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
