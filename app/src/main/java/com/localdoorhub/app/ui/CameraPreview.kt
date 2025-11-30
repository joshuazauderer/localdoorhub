package com.localdoorhub.app.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    CameraPreviewContent(
        modifier = modifier,
        context = context,
        lifecycleOwner = lifecycleOwner
    )
}

@Composable
private fun CameraPreviewContent(
    modifier: Modifier,
    context: Context,
    lifecycleOwner: LifecycleOwner
) {
    val configuration = LocalConfiguration.current
    val previewView = remember {
        PreviewView(context).apply {
            // Use COMPATIBLE mode for better compatibility
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val scope = rememberCoroutineScope()
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var previewUseCase: Preview? by remember { mutableStateOf(null) }
    
    // Get current display rotation and calculate target rotation based on orientation
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager }
    val displayRotation = remember { windowManager.defaultDisplay.rotation }
    
    // Calculate target rotation based on screen orientation
    val targetRotation = remember(configuration.orientation) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // For landscape, use display rotation directly
                displayRotation
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                // For portrait, use display rotation directly
                displayRotation
            }
            else -> displayRotation
        }
    }

    // Initialize camera only once, not on every orientation change
    LaunchedEffect(Unit) {
        try {
            val provider = context.getCameraProvider().also { cameraProvider = it }
            
            Log.d("CameraPreview", "Orientation: ${configuration.orientation}, Display rotation: $displayRotation, Target rotation: $targetRotation")
            
            val preview = Preview.Builder()
                .setTargetRotation(targetRotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            previewUseCase = preview

            // Try front camera first (emulator webcam is usually front)
            // Fall back to back camera if front fails
            var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                Log.d("CameraPreview", "Attempting to use FRONT camera with rotation $targetRotation")
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
                Log.d("CameraPreview", "Successfully using FRONT camera")
            } catch (e: Exception) {
                Log.e("CameraPreview", "Front camera failed: ${e.message}")
                // If front camera fails, try back camera
                try {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    Log.d("CameraPreview", "Attempting to use BACK camera")
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    Log.d("CameraPreview", "Successfully using BACK camera")
                } catch (e2: Exception) {
                    Log.e("CameraPreview", "Back camera also failed: ${e2.message}")
                    e2.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e("CameraPreview", "Error initializing camera: ${e.message}", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Clean up camera when composable is disposed
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener(
            {
                continuation.resume(future.get())
            },
            ContextCompat.getMainExecutor(this)
        )
    }
}

