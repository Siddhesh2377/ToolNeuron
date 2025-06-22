package com.dark.neuroverse.compose.screens.setup.permissions

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.compose.components.CheckBX
import com.dark.neuroverse.ui.theme.Warning
import com.dark.neuroverse.ui.theme.onWarning
import com.dark.neuroverse.utils.UserPrefs
import kotlinx.coroutines.launch

@Composable
fun PermissionScreen(
    paddingValues: PaddingValues,
    onNext: () -> Unit
) {
    var assPermission by remember { mutableStateOf(false) }
    var setAsDefault by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enabled = assPermission && setAsDefault

    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 34.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Final Setup..!",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Thank You For Patiently \nWaiting....!",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PermissionCard(
                    title = "Accessibility Permission",
                    description = "Accessibility Permission is Optional but, not giving it Android may Block Some of your Favourite Plugin",
                    checked = assPermission,
                    onCheckedChange = { assPermission = it },
                    isSkipAble = true,
                    onSkipClick = {

                    }
                ) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }

                PermissionCard(
                    title = "Set As Default Assistant",
                    description = "This Is Also Option If You don’t want to change your default assistant then you can just use NeuroV within the NeuroV app or assign a HW Button",
                    checked = setAsDefault,
                    onCheckedChange = { setAsDefault = it },
                    isSkipAble = true,
                    onSkipClick = {
                        scope.launch {
                            UserPrefs.setAssistantEnabled(context, false)
                        }
                    }
                ) {
                    val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                    context.startActivity(intent)
                    scope.launch {
                        UserPrefs.setAssistantEnabled(context, false)
                    }
                }
            }
        }

        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    "Neuro V",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { onNext() },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Let’s GO", fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isSkipAble: Boolean = false,
    onSkipClick: () -> Unit,
    onGrantClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                )

                CheckBX(
                    checked = checked,
                    isReadOnly = true,
                    onCheckStateChange = onCheckedChange
                )
            }

            Text(
                description,
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontFamily = FontFamily.Monospace,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isSkipAble) {
                    Button(
                        onClick = {
                            onCheckedChange(true)
                            onSkipClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = onWarning,
                            contentColor = Warning
                        ),
                        modifier = Modifier.width(120.dp)
                    ) {
                        Text("Skip", fontFamily = FontFamily.Serif)
                    }

                    Spacer(Modifier.width(10.dp))
                }

                Button(
                    onClick = {
                        onCheckedChange(true)
                        onGrantClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.width(120.dp)
                ) {
                    Text("Grant", fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}
