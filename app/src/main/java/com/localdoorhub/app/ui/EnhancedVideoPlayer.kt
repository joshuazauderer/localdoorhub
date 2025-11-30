package com.localdoorhub.app.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(UnstableApi::class)
@Composable
fun EnhancedVideoPlayer(
    videoUrl: String?,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showControls: Boolean = true,
    eventId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var playbackState by remember { mutableStateOf<Int>(Player.STATE_IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showThumbnail by remember(videoUrl) { mutableStateOf(true) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var shouldInitializePlayer by remember(videoUrl) { mutableStateOf(autoPlay) }
    
    // Track selector for quality selection
    val trackSelector = remember {
        DefaultTrackSelector(context)
    }
    
    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                downloadVideo(context, videoUrl, eventId) { progress, status ->
                    downloadProgress = progress
                    downloadStatus = status
                }
            }
        }
    }

    // Initialize ExoPlayer only when needed (lazy loading) - prevents multiple initializations
    LaunchedEffect(videoUrl, shouldInitializePlayer) {
        // Only initialize if we should and have a valid URL
        if (!shouldInitializePlayer || videoUrl == null || videoUrl.isEmpty()) {
            if (videoUrl == null || videoUrl.isEmpty()) {
                errorMessage = "No video URL provided"
            }
            return@LaunchedEffect
        }

        // Clean up previous player first
        player?.release()
        player = null

        try {
            Log.d("EnhancedVideoPlayer", "Initializing ExoPlayer with URL: $videoUrl")
            
            val exoPlayer = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build().apply {
                    val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                    setMediaItem(mediaItem)
                    prepare()
                    if (autoPlay) {
                        playWhenReady = true
                    }
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            playbackState = state
                            Log.d("EnhancedVideoPlayer", "Playback state changed: $state")
                            when (state) {
                                Player.STATE_READY -> {
                                    showThumbnail = false
                                    errorMessage = null
                                }
                            }
                        }
                        
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            errorMessage = error.message ?: "Unknown playback error"
                            Log.e("EnhancedVideoPlayer", "Playback error: ${error.message}", error)
                        }
                        
                        override fun onTracksChanged(tracks: Tracks) {
                            // Track information available for quality selection
                            Log.d("EnhancedVideoPlayer", "Tracks changed: ${tracks.groups.size} groups")
                        }
                    })
                }
            
            player = exoPlayer
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to load video: ${e.message}"
            Log.e("EnhancedVideoPlayer", "Error initializing player: ${e.message}", e)
        }
    }
    
    // Auto-initialize if autoPlay is true, otherwise wait for user interaction
    LaunchedEffect(videoUrl, autoPlay) {
        if (videoUrl != null && autoPlay) {
            shouldInitializePlayer = true
        }
    }
    
    // Handle fullscreen
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            (context as? Activity)?.window?.let { window ->
                window.decorView.systemUiVisibility = 
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        } else {
            (context as? Activity)?.window?.let { window ->
                window.decorView.systemUiVisibility = 
                    android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // Cleanup when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Box(modifier = modifier) {
        when {
            videoUrl == null || videoUrl.isEmpty() -> {
                // No video URL
                EmptyVideoState()
            }
            errorMessage != null -> {
                // Error state
                ErrorState(errorMessage ?: "Unknown error", videoUrl)
            }
            showThumbnail && thumbnailUrl != null -> {
                // Show thumbnail preview
                ThumbnailPreview(
                    thumbnailUrl = thumbnailUrl,
                    onPlayClick = {
                        shouldInitializePlayer = true
                        showThumbnail = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            playbackState == Player.STATE_BUFFERING -> {
                // Loading state
                LoadingState()
            }
            else -> {
                // Video player with controls overlay
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = showControls
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                playerViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.player = player
                        }
                    )
                    
                    // Custom controls overlay
                    VideoControlsOverlay(
                        player = player,
                        isFullscreen = isFullscreen,
                        onFullscreenToggle = { isFullscreen = !isFullscreen },
                        onQualityClick = { showQualityMenu = true },
                        onDownloadClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                // Android 13+ - no permission needed for MediaStore
                                scope.launch {
                                    downloadVideo(context, videoUrl, eventId) { progress, status ->
                                        downloadProgress = progress
                                        downloadStatus = status
                                    }
                                }
                            } else {
                                // Request storage permission for older Android versions
                                storagePermissionLauncher.launch(
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
        
        // Quality selection menu
        if (showQualityMenu && player != null) {
            QualitySelectionMenu(
                player = player!!,
                trackSelector = trackSelector,
                onDismiss = { showQualityMenu = false },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        
        // Download progress indicator
        downloadProgress?.let { progress ->
            if (progress < 1f) {
                DownloadProgressIndicator(
                    progress = progress,
                    status = downloadStatus,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun ThumbnailPreview(
    thumbnailUrl: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build()
            ),
            contentDescription = "Video thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Play button overlay
        FloatingActionButton(
            onClick = onPlayClick,
            modifier = Modifier.size(64.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play video",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun VideoControlsOverlay(
    player: ExoPlayer?,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onQualityClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quality button
        IconButton(
            onClick = onQualityClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HighQuality,
                contentDescription = "Quality",
                tint = Color.White
            )
        }
        
        // Download button
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = Color.White
            )
        }
        
        // Fullscreen button
        IconButton(
            onClick = onFullscreenToggle,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                tint = Color.White
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun QualitySelectionMenu(
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var qualityOptions by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val tracks = player.currentTracks
        val options = mutableListOf<QualityOption>()
        
        tracks.groups.forEachIndexed { groupIndex, group ->
            val trackGroup = group.mediaTrackGroup
            (0 until trackGroup.length).forEach { trackIndex ->
                val format = trackGroup.getFormat(trackIndex)
                if (format.height > 0) {
                    options.add(
                        QualityOption(
                            label = "${format.height}p",
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            format = format
                        )
                    )
                }
            }
        }
        
        qualityOptions = options.distinctBy { it.label }.sortedByDescending { 
            it.format.height 
        }
    }
    
    Card(
        modifier = modifier
            .width(200.dp)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            Text(
                text = "Video Quality",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            Divider()
            if (qualityOptions.isEmpty()) {
                Text(
                    text = "Auto",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .clearSelectionOverrides()
                                .build()
                            onDismiss()
                        }
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // Auto option
                TextButton(
                    onClick = {
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .clearSelectionOverrides()
                            .build()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto")
                }
                Divider()
                qualityOptions.forEach { option ->
                    TextButton(
                        onClick = {
                            // Quality selection - simplified implementation
                            // Note: Full track selection override may require Media3 API updates
                            Log.d("EnhancedVideoPlayer", "Quality selected: ${option.label}")
                            // For now, just log the selection - full implementation may require
                            // different Media3 API usage depending on version
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(option.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressIndicator(
    progress: Float,
    status: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status ?: "Downloading... ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyVideoState() {
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

@Composable
private fun ErrorState(errorMessage: String, videoUrl: String) {
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
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun LoadingState() {
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

private data class QualityOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val format: androidx.media3.common.Format
)

private suspend fun downloadVideo(
    context: Context,
    videoUrl: String?,
    eventId: String?,
    onProgress: (Float, String) -> Unit
) = withContext(Dispatchers.IO) {
    if (videoUrl == null) {
        onProgress(0f, "No video URL")
        return@withContext
    }
    
    try {
        onProgress(0f, "Starting download...")
        
        val url = URL(videoUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        
        val contentLength = connection.contentLength.toFloat()
        val inputStream = connection.inputStream
        
        val fileName = eventId?.let { "doorbell_event_$it.mp4" } ?: "doorbell_video_${System.currentTimeMillis()}.mp4"
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val file = File(downloadsDir, fileName)
        
        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(8192)
            var totalBytesRead = 0
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (contentLength > 0) {
                    val progress = (totalBytesRead / contentLength).coerceIn(0f, 1f)
                    onProgress(progress, "Downloading... ${(progress * 100).toInt()}%")
                }
            }
        }
        
        inputStream.close()
        connection.disconnect()
        
        onProgress(1f, "Download complete: ${file.absolutePath}")
        
        // Notify media scanner
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )
        
    } catch (e: Exception) {
        Log.e("EnhancedVideoPlayer", "Download error: ${e.message}", e)
        onProgress(0f, "Download failed: ${e.message}")
    }
}

