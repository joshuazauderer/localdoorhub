const express = require('express');
const cors = require('cors');
const multer = require('multer');
const app = express();
const PORT = process.env.PORT || 3002;

// Configure multer for audio file uploads
const upload = multer({ 
    storage: multer.memoryStorage(),
    limits: { fileSize: 10 * 1024 * 1024 } // 10MB limit
});

// Enable CORS for all routes
app.use(cors());
app.use(express.json());

// In-memory state
let doorName = "Front Door";
let doorbellOnline = true;
let activeCall = false;
let lastDoorbellPressTime = null;

// Mock events data
const mockEvents = [
    {
        id: "evt_000",
        type: "doorbell_press",
        timestamp: new Date(Date.now() - 30 * 1000).toISOString(), // 30 seconds ago (recent for testing)
        thumbnailUrl: "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" // Test video URL
    },
    {
        id: "evt_001",
        type: "doorbell_press",
        timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString(), // 5 minutes ago
        thumbnailUrl: "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" // Use test video URL
    },
    {
        id: "evt_002",
        type: "motion",
        timestamp: new Date(Date.now() - 15 * 60 * 1000).toISOString(), // 15 minutes ago
        thumbnailUrl: "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" // Use test video URL
    },
    {
        id: "evt_003",
        type: "doorbell_press",
        timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2 hours ago
        thumbnailUrl: "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" // Use test video URL
    },
    {
        id: "evt_004",
        type: "motion",
        timestamp: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(), // 3 hours ago
        thumbnailUrl: "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4" // Use test video URL
    },
    {
        id: "evt_005",
        type: "doorbell_press",
        timestamp: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(), // 1 day ago
        thumbnailUrl: "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4" // Use test video URL
    }
];

// GET /api/v1/status
app.get('/api/v1/status', (req, res) => {
    console.log(`[${new Date().toISOString()}] GET /api/v1/status - Returning doorbell status`);
    res.json({
        doorbellOnline: doorbellOnline,
        doorName: doorName,
        batteryPercent: 82,
        wifiStrength: "good",
        rtspUrl: "rtsp://192.168.8.10:554/live"
    });
});

// GET /api/v1/events
app.get('/api/v1/events', (req, res) => {
    console.log(`[${new Date().toISOString()}] GET /api/v1/events - Returning ${mockEvents.length} events`);
    // Return events sorted by timestamp (newest first)
    const sortedEvents = [...mockEvents].sort((a, b) => {
        return new Date(b.timestamp) - new Date(a.timestamp);
    });
    
    // Update lastDoorbellPressTime if there's a recent doorbell_press event
    const recentPress = sortedEvents.find(e => 
        e.type === "doorbell_press" && 
        (Date.now() - new Date(e.timestamp).getTime()) < 2 * 60 * 1000
    );
    if (recentPress && (!lastDoorbellPressTime || new Date(recentPress.timestamp).getTime() > lastDoorbellPressTime)) {
        lastDoorbellPressTime = new Date(recentPress.timestamp).getTime();
    }
    
    res.json(sortedEvents);
});

// POST /api/v1/test-chime
app.post('/api/v1/test-chime', (req, res) => {
    console.log(`[${new Date().toISOString()}] POST /api/v1/test-chime - MOCK: Simulating chime test (no actual sound will play)`);
    // Simulate a small delay
    setTimeout(() => {
        res.json({ success: true });
    }, 500);
});

// POST /api/v1/test-stream
app.post('/api/v1/test-stream', (req, res) => {
    console.log(`[${new Date().toISOString()}] POST /api/v1/test-stream - MOCK: Simulating video stream test (no actual video will play)`);
    // Simulate a small delay
    setTimeout(() => {
        res.json({ success: true });
    }, 500);
});

// POST /api/v1/finish-setup
app.post('/api/v1/finish-setup', (req, res) => {
    const { doorName: newDoorName } = req.body;
    console.log(`[${new Date().toISOString()}] POST /api/v1/finish-setup - Door name: ${newDoorName || 'not provided'}`);
    if (newDoorName) {
        doorName = newDoorName;
        console.log(`  → Door name updated to: ${doorName}`);
    }
    res.json({ success: true });
});

// GET /api/v1/active-call - Check if there's an active call
app.get('/api/v1/active-call', (req, res) => {
    // Check if there's been a doorbell press in the last 2 minutes
    const twoMinutesAgo = Date.now() - 2 * 60 * 1000;
    
    // Check events for recent doorbell presses
    const recentPress = mockEvents.find(e => 
        e.type === "doorbell_press" && 
        (Date.now() - new Date(e.timestamp).getTime()) < 2 * 60 * 1000
    );
    const hasRecentPress = recentPress != null || (lastDoorbellPressTime && lastDoorbellPressTime > twoMinutesAgo);
    const isActive = activeCall || hasRecentPress;
    
    console.log(`[${new Date().toISOString()}] GET /api/v1/active-call - Active: ${isActive}, Call: ${activeCall}, Recent press: ${hasRecentPress}`);
    res.json({ 
        active: isActive,
        callActive: activeCall,
        recentPress: hasRecentPress
    });
});

// POST /api/v1/start-call - Start a call session
app.post('/api/v1/start-call', (req, res) => {
    console.log(`[${new Date().toISOString()}] POST /api/v1/start-call - Starting call session`);
    activeCall = true;
    res.json({ success: true, active: true });
});

// POST /api/v1/end-call - End a call session
app.post('/api/v1/end-call', (req, res) => {
    console.log(`[${new Date().toISOString()}] POST /api/v1/end-call - Ending call session`);
    activeCall = false;
    res.json({ success: true, active: false });
});

// POST /api/v1/simulate-doorbell-press - Simulate a doorbell press for testing
app.post('/api/v1/simulate-doorbell-press', (req, res) => {
    const now = new Date();
    const newEvent = {
        id: `evt_sim_${Date.now()}`,
        type: "doorbell_press",
        timestamp: now.toISOString(),
        thumbnailUrl: "http://192.168.8.1/media/thumbnails/simulated.jpg"
    };
    
    // Add to beginning of array (will be sorted by timestamp)
    mockEvents.unshift(newEvent);
    lastDoorbellPressTime = now.getTime();
    
    console.log(`[${now.toISOString()}] POST /api/v1/simulate-doorbell-press - Simulated doorbell press`);
    console.log(`  → Event ID: ${newEvent.id}`);
    console.log(`  → Talk button should now appear in the app`);
    
    res.json({ 
        success: true, 
        event: newEvent,
        message: "Doorbell press simulated. Talk button should appear within 2 seconds."
    });
});

// POST /api/v1/talk - Send audio to doorbell for two-way communication
app.post('/api/v1/talk', upload.single('audio'), (req, res) => {
    console.log(`\n${'='.repeat(60)}`);
    console.log(`[${new Date().toISOString()}] 🎤 AUDIO RECEIVED FROM APP`);
    console.log(`${'='.repeat(60)}`);
    
    if (req.file) {
        const sizeKB = (req.file.size / 1024).toFixed(2);
        console.log(`  ✅ Audio file received:`);
        console.log(`     • Filename: ${req.file.originalname || 'audio.m4a'}`);
        console.log(`     • Size: ${req.file.size} bytes (${sizeKB} KB)`);
        console.log(`     • MIME Type: ${req.file.mimetype || 'audio/mp4'}`);
        console.log(`  🔊 MOCK: Audio would be played through doorbell speaker`);
        console.log(`  📝 In production, this audio would be:`);
        console.log(`     • Decoded and played on the doorbell's speaker`);
        console.log(`     • Broadcast to anyone near the doorbell`);
        console.log(`  ✅ Talk functionality is WORKING!`);
    } else {
        console.log(`  ⚠️  Warning: No audio file received in request`);
    }
    
    console.log(`${'='.repeat(60)}\n`);
    
    // Simulate processing delay
    setTimeout(() => {
        res.json({ 
            success: true,
            message: "Audio message sent to doorbell",
            timestamp: new Date().toISOString()
        });
    }, 300);
});

// GET /api/v1/video/{eventId} - Get video URL for an event
app.get('/api/v1/video/:eventId', (req, res) => {
    const eventId = req.params.eventId;
    const event = mockEvents.find(e => e.id === eventId);
    
    if (!event) {
        return res.status(404).json({ error: "Event not found" });
    }
    
    // Construct video URL from thumbnail URL or return a test video
    let videoUrl = event.thumbnailUrl;
    if (videoUrl && videoUrl.includes("/thumbnails/")) {
        videoUrl = videoUrl.replace("/thumbnails/", "/videos/").replace(/\.(jpg|jpeg|png)$/, ".mp4");
    }
    
    // If no video URL, return a test video
    if (!videoUrl) {
        videoUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
    }
    
    console.log(`[${new Date().toISOString()}] GET /api/v1/video/${eventId} - Returning video URL: ${videoUrl}`);
    res.json({ videoUrl: videoUrl });
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`LocalDoorHub Mock API Server running on http://0.0.0.0:${PORT}`);
    console.log(`Available endpoints:`);
    console.log(`  GET  /api/v1/status`);
    console.log(`  GET  /api/v1/events`);
    console.log(`  GET  /api/v1/active-call`);
    console.log(`  GET  /api/v1/video/:eventId (get video URL for event)`);
    console.log(`  POST /api/v1/start-call`);
    console.log(`  POST /api/v1/end-call`);
    console.log(`  POST /api/v1/simulate-doorbell-press (for testing)`);
    console.log(`  POST /api/v1/test-chime`);
    console.log(`  POST /api/v1/test-stream`);
    console.log(`  POST /api/v1/finish-setup`);
    console.log(`  POST /api/v1/talk (multipart/form-data with audio file)`);
    console.log(`\nNote: Update the baseUrl in DoorbellRepository to http://YOUR_COMPUTER_IP:${PORT}`);
});

