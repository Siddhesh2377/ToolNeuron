package com.dark.neuroverse.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.ActionColors
import com.dark.neuroverse.ui.theme.NVSpacing

@Composable
fun CollapsableButton(
    collapse: Boolean = false,
    text: String = "New Chat",
    enabled: Boolean = true,
    icon: ImageVector = Icons.Default.AccountTree,
    onClick: () -> Unit = {}
) {
    AnimatedContent(collapse, transitionSpec = {
        fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
    }, label = "Button Animation") {
        when (it) {
            true -> {
                IconButton(
                    enabled = enabled, onClick = {
                        onClick()
                    }, colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        icon, "settings", modifier = Modifier
                    )
                }
            }

            false -> {
                Button(
                    enabled = enabled,
                    onClick = {
                        onClick()
                    },
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text, fontFamily = FontFamily.Serif)

                        Icon(icon, "")
                    }
                }
            }
        }
    }
}