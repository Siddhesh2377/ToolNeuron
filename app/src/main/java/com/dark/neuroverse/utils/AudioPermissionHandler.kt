package com.dark.neuroverse.utils

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberAudioPermissionState(): Pair<State<Boolean>, () -> Unit> {
    val context = LocalContext.current
    val hasPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("Permission", "Permission result: $granted")
        hasPermission.value = granted
    }


    val requestPermission = remember(launcher) {
        { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }


    // Only auto-request once on first render if not granted
    LaunchedEffect(Unit) {
        if (!hasPermission.value) requestPermission()
    }

    return hasPermission to requestPermission
}
