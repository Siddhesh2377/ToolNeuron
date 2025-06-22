package com.dark.neuroverse.compose.screens.home

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dark.neuroverse.data.fullTermsText
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.utils.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showTerms by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }
    var acceptChecked by remember { mutableStateOf(false) }
    var openAssistant by remember { mutableStateOf(false) }

//    var hasAudioPermission by remember {
//        mutableStateOf(
//            ContextCompat.checkSelfPermission(
//                context,
//                Manifest.permission.RECORD_AUDIO
//            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
//        )
//    }

    LaunchedEffect(Unit) {
        val accepted = UserPrefs.isTermsAccepted(context).first()
        termsAccepted = accepted

        val assistantEnabled = UserPrefs.isAssistantEnabled(context).first()
        openAssistant = assistantEnabled

        if (!accepted) {
            showTerms = true
        }
    }

    // Prevent immediate jump to Settings if not yet accepted

    LaunchedEffect(openAssistant) {
        if (openAssistant) {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            context.startActivity(intent)
            Log.d("HomeScreen", "Opening Assistant Settings")
            UserPrefs.setAssistantEnabled(context, false)
            openAssistant = false
        }
    }


//    val audioPermissionLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.RequestPermission(),
//        onResult = { isGranted ->
//            hasAudioPermission = isGranted
//            if (isGranted) {
//                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
//                context.startActivity(intent)
//            }
//        }
//    )

    NeuroVerseTheme {
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ElevatedButton(
                onClick = {
                    if (termsAccepted) {

                    } else {
                        showTerms = true
                    }
                },
                enabled = true,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text(
                    if (termsAccepted) "Open Plugin Manager" else "Setup NeuroV",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(20.dp))

            ElevatedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                enabled = true,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text(
                    "Setup NeuroV",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { showTerms = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "View Terms & Conditions",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (showTerms) {
            AlertDialog(
                onDismissRequest = {
                    // Always allow dismiss manually
                    showTerms = false
                },
                icon = {
                    Icon(
                        imageVector = Icons.TwoTone.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        "Terms & Conditions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(min = 150.dp, max = 400.dp)
                            .widthIn(min = 300.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = fullTermsText(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp)
                        ) {
                            Checkbox(
                                checked = acceptChecked,
                                onCheckedChange = { acceptChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "I accept the Terms & Conditions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (acceptChecked) {
                                scope.launch {
                                    UserPrefs.setTermsAccepted(context, true)
                                    termsAccepted = true
                                    showTerms = false
                                    openAssistant = true
                                }
                            }
                        },
                        enabled = acceptChecked,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Continue")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                ),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            )
        }
    }
}
