package com.dark.tool_neuron.ui.screens.password_screen

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.dark.tool_neuron.ui.icons.TnIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreenTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Unlock",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            Icon(
                imageVector = TnIcons.Shield,
                contentDescription = "Security",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}
