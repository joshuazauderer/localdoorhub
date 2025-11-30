package com.localdoorhub.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberCameraPermissionState(): PermissionState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    return remember(hasPermission) {
        object : PermissionState {
            override val hasPermission: Boolean = hasPermission
            override fun requestPermission() {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

@Composable
fun rememberAudioPermissionState(): PermissionState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    return remember(hasPermission) {
        object : PermissionState {
            override val hasPermission: Boolean = hasPermission
            override fun requestPermission() {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

interface PermissionState {
    val hasPermission: Boolean
    fun requestPermission()
}

