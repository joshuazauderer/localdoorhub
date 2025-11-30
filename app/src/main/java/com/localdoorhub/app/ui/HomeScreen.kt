package com.localdoorhub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdoorhub.app.R
import com.localdoorhub.app.data.DoorbellRepository
import com.localdoorhub.app.data.local.PreferencesManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToEvents: () -> Unit = {},
    onNavigateToEventDetail: (String) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    repo: DoorbellRepository = DoorbellRepository(),
    prefsManager: PreferencesManager = PreferencesManager(LocalContext.current)
) {
    var status by remember { mutableStateOf<com.localdoorhub.app.data.models.DoorbellStatus?>(null) }
    var events by remember { mutableStateOf<List<com.localdoorhub.app.data.models.DoorbellEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasActiveCall by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val doorName = prefsManager.getDoorName() ?: "Front Door"

    // Refresh data when refreshKey changes - run on background thread to prevent blocking
    LaunchedEffect(refreshKey) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                isLoading = true
            }
            val newStatus = repo.getStatus()
            val newEvents = repo.getEvents().take(5) // Show last 5 events
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                status = newStatus
                events = newEvents
                isLoading = false
            }
        }
    }

    // Poll for active calls every 10 seconds (on background thread) - further reduced for better performance
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                delay(10000) // Poll every 10 seconds (reduced from 5 seconds)
                try {
                    val active = repo.hasActiveCall()
                    // Update state on main thread
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        hasActiveCall = active
                    }
                } catch (e: Exception) {
                    // Silently handle errors, keep previous state
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(32.dp)) // Balance the layout
            Text(
                text = doorName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Image(
                painter = painterResource(id = R.drawable.ic_localdoorhub_logo),
                contentDescription = "Logo - Tap to return to home",
                modifier = Modifier
                    .size(32.dp)
                    .clickable {
                        // Refresh the home screen when logo is clicked
                        refreshKey++
                        // Also navigate to home if not already there (for consistency)
                        onNavigateToHome()
                    }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live View Card - Camera Preview with Talk Button
        // DISABLED: Camera preview causes severe performance issues in emulator
        // TODO: Re-enable when testing on physical device
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.live_view),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera preview disabled in emulator",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Talk button overlay - bottom right (only show when there's an active call)
                    if (hasActiveCall) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TalkButton(
                            repo = repo,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Events Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_events),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToEvents) {
                        Text("View All")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (events.isEmpty()) {
                    Text(
                        text = "No events yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    events.forEach { event ->
                        EventItem(
                            event = event,
                            onClick = { onNavigateToEventDetail(event.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Status Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (status != null) {
                if (status?.batteryPercent != null) {
                    Text(
                        text = stringResource(R.string.doorbell_battery, status!!.batteryPercent!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(
                        R.string.wifi_status,
                        status?.wifiStrength?.let {
                            if (it == "good") stringResource(R.string.wifi_good)
                            else stringResource(R.string.wifi_poor)
                        } ?: "Unknown"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventItem(
    event: com.localdoorhub.app.data.models.DoorbellEvent,
    onClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val timestamp = try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(event.timestamp)
        date?.let { dateFormat.format(it) } ?: event.timestamp
    } catch (e: Exception) {
        event.timestamp
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (event.type) {
                        "doorbell_press" -> stringResource(R.string.doorbell_press)
                        "motion" -> stringResource(R.string.motion)
                        else -> event.type
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

