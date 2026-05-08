package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.theme.LocalDimens

@Composable
fun TermsConditionsBottomBar(
    buttonLabel: String,
    onAccept: () -> Unit,
) {
    val dimens = LocalDimens.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimens.screenPadding,
                    vertical = dimens.spacingMd,
                ),
        ) {
            Text(
                text = buttonLabel,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
