package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsConditionsTopBar() {
    val dimens = LocalDimens.current
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Terms of use",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            Icon(
                imageVector = TnIcons.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = dimens.screenPadding),
            )
        },
    )
}
