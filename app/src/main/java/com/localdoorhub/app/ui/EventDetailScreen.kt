package com.localdoorhub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdoorhub.app.R
import com.localdoorhub.app.data.DoorbellRepository
import com.localdoorhub.app.data.convertUrlForEmulator
import com.localdoorhub.app.ui.EnhancedVideoPlayer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    repo: DoorbellRepository = DoorbellRepository()
) {
    var event by remember { mutableStateOf<com.localdoorhub.app.data.models.DoorbellEvent?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(eventId) {
        val events = repo.getEvents()
        event = events.find { it.id == eventId }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_localdoorhub_logo),
                        contentDescription = "Logo - Tap to return to home",
                        modifier = Modifier
                            .size(32.dp)
                            .padding(8.dp)
                            .clickable { onNavigateToHome() }
                    )
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (event == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Event not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val eventData = event!!
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
            val timestamp = try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(eventData.timestamp)
                date?.let { dateFormat.format(it) } ?: eventData.timestamp
            } catch (e: Exception) {
                eventData.timestamp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Event Type Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = when (eventData.type) {
                                "doorbell_press" -> stringResource(R.string.doorbell_press)
                                "motion" -> stringResource(R.string.motion)
                                else -> eventData.type.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Enhanced Video Player Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    // Determine video URL:
                    // 1. If thumbnailUrl ends with .mp4/.mov/.webm, use it directly (already a video URL)
                    // 2. If thumbnailUrl contains /thumbnails/, try to construct video URL
                    // 3. Otherwise, use thumbnailUrl as-is (might be a video URL)
                    val videoUrl = eventData.thumbnailUrl?.let { thumbnailUrl ->
                        android.util.Log.d("EventDetailScreen", "Event ${eventData.id} thumbnailUrl: $thumbnailUrl")
                        
                        val rawUrl = when {
                            // Check if it's already a video URL (ends with video extension or is RTSP)
                            thumbnailUrl.endsWith(".mp4", ignoreCase = true) ||
                            thumbnailUrl.endsWith(".mov", ignoreCase = true) ||
                            thumbnailUrl.endsWith(".webm", ignoreCase = true) ||
                            thumbnailUrl.startsWith("rtsp://", ignoreCase = true) -> {
                                // Already a video URL - use it directly
                                android.util.Log.d("EventDetailScreen", "Using thumbnailUrl directly as video URL: $thumbnailUrl")
                                thumbnailUrl
                            }
                            // Check if it's a thumbnail URL that needs conversion
                            thumbnailUrl.contains("/thumbnails/") -> {
                                // Try to construct video URL from thumbnail URL
                                val constructed = thumbnailUrl
                                    .replace("/thumbnails/", "/videos/")
                                    .replace(Regex("\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE), ".mp4")
                                android.util.Log.d("EventDetailScreen", "Constructed video URL from thumbnail: $constructed")
                                constructed
                            }
                            // For external URLs (like Google Cloud Storage), use as-is
                            thumbnailUrl.startsWith("http://", ignoreCase = true) || 
                            thumbnailUrl.startsWith("https://", ignoreCase = true) -> {
                                android.util.Log.d("EventDetailScreen", "Using external URL as video URL: $thumbnailUrl")
                                thumbnailUrl
                            }
                            else -> {
                                // Use as-is (might be a video URL)
                                android.util.Log.d("EventDetailScreen", "Using thumbnailUrl as-is: $thumbnailUrl")
                                thumbnailUrl
                            }
                        }
                        // Convert URL for emulator compatibility (192.168.8.1 -> 10.0.2.2:3002)
                        // External URLs won't be converted
                        val finalUrl = convertUrlForEmulator(rawUrl)
                        android.util.Log.d("EventDetailScreen", "Final video URL: $finalUrl")
                        finalUrl
                    }
                    
                    // Extract thumbnail URL (original, before conversion to video)
                    // Convert for emulator compatibility
                    val thumbnailUrlForPreview = if (eventData.thumbnailUrl != null && 
                        !eventData.thumbnailUrl.endsWith(".mp4", ignoreCase = true) &&
                        !eventData.thumbnailUrl.endsWith(".mov", ignoreCase = true) &&
                        !eventData.thumbnailUrl.endsWith(".webm", ignoreCase = true) &&
                        !eventData.thumbnailUrl.startsWith("rtsp://", ignoreCase = true)) {
                        convertUrlForEmulator(eventData.thumbnailUrl)
                    } else {
                        null
                    }
                    
                    EnhancedVideoPlayer(
                        videoUrl = videoUrl,
                        thumbnailUrl = thumbnailUrlForPreview,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = false,
                        showControls = true,
                        eventId = eventData.id
                    )
                }

                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Event Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Event ID: ${eventData.id}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Type: ${eventData.type}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (eventData.thumbnailUrl != null) {
                            Text(
                                text = "Media URL: ${eventData.thumbnailUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

