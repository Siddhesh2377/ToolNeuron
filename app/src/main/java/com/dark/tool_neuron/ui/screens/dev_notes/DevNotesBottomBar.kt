package com.dark.tool_neuron.ui.screens.dev_notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevNotesBottomBar(navController: NavHostController, onContinue: () -> Unit = {}) {
    val dimens = LocalDimens.current
    val isOnboarding = navController.previousBackStackEntry?.destination?.route !=
        NavScreens.HomeScreen.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    .compositeOver(MaterialTheme.colorScheme.background)
            )
            .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (isOnboarding) "Ready to explore?" else "All caught up?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isOnboarding) "Tap continue to open the app" else "Tap back to return to chat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ActionTextButton(
            onClickListener = {
                if (isOnboarding) {
                    onContinue()
                    navController.navigate(NavScreens.SetupScreen.route) {
                        popUpTo(NavScreens.DevNotes.route) { inclusive = true }
                    }
                } else {
                    navController.popBackStack()
                }
            },
            icon = if (isOnboarding) TnIcons.ChevronDown else TnIcons.ArrowLeft,
            text = if (isOnboarding) "Continue" else "Back"
        )
    }
}
