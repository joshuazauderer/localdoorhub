package com.localdoorhub.app.ui

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localdoorhub.app.data.DoorbellRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TalkButton(
    repo: DoorbellRepository = DoorbellRepository(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCallActive by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingJob: Job? by remember { mutableStateOf(null) }
    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var audioFile: File? by remember { mutableStateOf(null) }
    val stopSignal = remember { Channel<Unit>(Channel.CONFLATED) }
    
    val audioPermissionState = rememberAudioPermissionState()
    val hasPermission = audioPermissionState.hasPermission

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasPermission) {
            Button(
                onClick = { audioPermissionState.requestPermission() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Enable Microphone")
            }
            Text(
                text = "Microphone permission needed for two-way talk",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Talk button - toggle mode
            Button(
                onClick = {
                    scope.launch {
                        if (!isCallActive) {
                            // Start call
                            val success = repo.startCall()
                            if (success) {
                                isCallActive = true
                                // Start continuous recording
                                isRecording = true
                                recordingJob = scope.launch {
                                    try {
                                        startRecording(context, repo, { recorder, file ->
                                            mediaRecorder = recorder
                                            audioFile = file
                                        }, stopSignal) {
                                            // Cleanup callback
                                            mediaRecorder = null
                                            audioFile = null
                                            isRecording = false
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        Log.d("TalkButton", "Recording job cancelled (expected)")
                                    } catch (e: Exception) {
                                        Log.e("TalkButton", "Error in recording job: ${e.message}", e)
                                        mediaRecorder?.release()
                                        mediaRecorder = null
                                        audioFile?.delete()
                                        audioFile = null
                                        isRecording = false
                                    }
                                }
                            }
                        } else {
                            // End call
                            val recorder = mediaRecorder
                            val file = audioFile
                            
                            // Stop the recorder first
                            if (recorder != null) {
                                try {
                                    recorder.stop()
                                    Log.d("TalkButton", "Recorder stopped successfully")
                                } catch (e: IllegalStateException) {
                                    Log.d("TalkButton", "Recorder already stopped or not recording: ${e.message}")
                                } catch (e: Exception) {
                                    Log.e("TalkButton", "Error stopping recorder: ${e.message}", e)
                                }
                                try {
                                    recorder.release()
                                    Log.d("TalkButton", "Recorder released successfully")
                                } catch (e: Exception) {
                                    Log.e("TalkButton", "Error releasing recorder: ${e.message}", e)
                                }
                            }
                            
                            // Send stop signal and cancel job
                            stopSignal.trySend(Unit)
                            recordingJob?.cancel()
                            mediaRecorder = null
                            
                            // Send final audio file if it exists and has content
                            file?.let { audioFile ->
                                kotlinx.coroutines.delay(500)
                                if (audioFile.exists() && audioFile.length() > 0) {
                                    Log.d("TalkButton", "Sending final audio file: ${audioFile.length()} bytes")
                                    sendAudioToDoorbell(repo, audioFile)
                                }
                                try {
                                    audioFile.delete()
                                } catch (e: Exception) {
                                    Log.e("TalkButton", "Error deleting temp file: ${e.message}", e)
                                }
                            }
                            audioFile = null
                            isRecording = false
                            
                            // End call on server
                            repo.endCall()
                            isCallActive = false
                        }
                    }
                },
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCallActive) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isRecording && isCallActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = if (isCallActive) "END TALK" else "TALK",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            
            Text(
                text = if (isCallActive) "Call active - Tap to end" else "Tap to start talking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun startRecording(
    context: Context,
    repo: DoorbellRepository,
    onRecorderReady: (MediaRecorder, File) -> Unit,
    stopSignal: Channel<Unit>,
    onFinished: () -> Unit
) = withContext(Dispatchers.IO) {
    var mediaRecorder: MediaRecorder? = null
    var audioFile: File? = null
    
    try {
        // Create temporary audio file
        audioFile = File(context.cacheDir, "doorbell_audio_${System.currentTimeMillis()}.m4a")
        
        Log.d("TalkButton", "Initializing MediaRecorder...")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
        
        Log.d("TalkButton", "Recording started successfully, file: ${audioFile.absolutePath}")
        onRecorderReady(mediaRecorder!!, audioFile!!)
        
        // Keep recording until stop signal is received
        // Use withTimeout to prevent infinite wait, but allow cancellation
        try {
            kotlinx.coroutines.withTimeout(60000) { // Max 60 seconds
                stopSignal.receive()
            }
            Log.d("TalkButton", "Stop signal received in coroutine")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("TalkButton", "Recording timeout - stopping after 60 seconds")
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("TalkButton", "Recording cancelled before stop signal received")
        }
        
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Expected when button is released or job cancelled
        Log.d("TalkButton", "Recording coroutine cancelled")
    } catch (e: Exception) {
        Log.e("TalkButton", "Error recording audio: ${e.message}", e)
        e.printStackTrace()
        try {
            mediaRecorder?.stop()
        } catch (stopException: Exception) {
            Log.e("TalkButton", "Error stopping recorder: ${stopException.message}")
        }
        try {
            mediaRecorder?.release()
        } catch (releaseException: Exception) {
            Log.e("TalkButton", "Error releasing recorder: ${releaseException.message}", releaseException)
        }
        audioFile?.delete()
    } finally {
        onFinished()
    }
}

private suspend fun sendAudioToDoorbell(
    repo: DoorbellRepository,
    audioFile: File
) = withContext(Dispatchers.IO) {
    try {
        val fileSize = audioFile.length()
        Log.d("TalkButton", "═══════════════════════════════════════")
        Log.d("TalkButton", "🎤 SENDING AUDIO TO DOORBELL")
        Log.d("TalkButton", "   File: ${audioFile.name}")
        Log.d("TalkButton", "   Size: $fileSize bytes (${fileSize / 1024} KB)")
        Log.d("TalkButton", "   Path: ${audioFile.absolutePath}")
        
        val success = repo.sendAudio(audioFile)
        
        if (success) {
            Log.d("TalkButton", "✅ Audio sent successfully!")
            Log.d("TalkButton", "   The doorbell should have received and played the audio")
        } else {
            Log.e("TalkButton", "❌ Failed to send audio - check server logs")
        }
        Log.d("TalkButton", "═══════════════════════════════════════")
    } catch (e: Exception) {
        Log.e("TalkButton", "❌ Error sending audio: ${e.message}", e)
    }
}

