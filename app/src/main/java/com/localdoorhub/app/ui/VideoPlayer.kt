package com.localdoorhub.app.ui

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(
    videoUrl: String?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    showControls: Boolean = true
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackState by remember { mutableStateOf<Int>(Player.STATE_IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Initialize ExoPlayer
    LaunchedEffect(videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            errorMessage = "No video URL provided"
            return@LaunchedEffect
        }

        try {
            Log.d("VideoPlayer", "Initializing ExoPlayer with URL: $videoUrl")
            
            val exoPlayer = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                setMediaItem(mediaItem)
                prepare()
                if (autoPlay) {
                    playWhenReady = true
                }
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        playbackState = state
                        Log.d("VideoPlayer", "Playback state changed: $state")
                        when (state) {
                            Player.STATE_IDLE -> Log.d("VideoPlayer", "State: IDLE")
                            Player.STATE_BUFFERING -> Log.d("VideoPlayer", "State: BUFFERING")
                            Player.STATE_READY -> {
                                Log.d("VideoPlayer", "State: READY")
                                errorMessage = null
                            }
                            Player.STATE_ENDED -> Log.d("VideoPlayer", "State: ENDED")
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        errorMessage = error.message ?: "Unknown playback error"
                        Log.e("VideoPlayer", "Playback error: ${error.message}", error)
                    }
                })
            }
            
            player = exoPlayer
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to load video: ${e.message}"
            Log.e("VideoPlayer", "Error initializing player: ${e.message}", e)
        }
    }

    // Cleanup when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            videoUrl == null || videoUrl.isEmpty() -> {
                // No video URL
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📷",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No video available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            errorMessage != null -> {
                // Error state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Video Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "URL: $videoUrl",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            playbackState == Player.STATE_BUFFERING -> {
                // Loading state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading video...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                // Video player
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = showControls
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
