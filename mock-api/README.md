# LocalDoorHub Mock API Server

A simple mock API server for LocalDoorHub Android app development and testing.

## Setup

1. Install dependencies:
```bash
npm install
```

2. Start the server:
```bash
npm start
```

For development with auto-reload:
```bash
npm run dev
```

## Configuration

The server runs on port 3001 by default. To use it with the Android app:

1. Find your computer's IP address on your local network
2. Update the `baseUrl` in `DoorbellRepository.kt` to `http://YOUR_IP:3001`
3. Make sure your Android device/emulator is on the same network

## Endpoints

- `GET /api/v1/status` - Returns doorbell status
- `GET /api/v1/events` - Returns list of events
- `POST /api/v1/test-chime` - Test chime endpoint
- `POST /api/v1/test-stream` - Test stream endpoint
- `POST /api/v1/finish-setup` - Complete setup (updates door name)

## Example Response

### GET /api/v1/status
```json
{
  "doorbellOnline": true,
  "doorName": "Front Door",
  "batteryPercent": 82,
  "wifiStrength": "good",
  "rtspUrl": "rtsp://192.168.8.10:554/live"
}
```

### GET /api/v1/events
```json
[
  {
    "id": "evt_001",
    "type": "doorbell_press",
    "timestamp": "2025-11-29T20:01:32Z",
    "thumbnailUrl": "http://192.168.8.1/media/thumbnails/evt_001.jpg"
  }
]
```

## Notes

- The server stores door name in memory (resets on restart)
- Events are pre-populated with mock data
- All endpoints return success responses for testing purposes

