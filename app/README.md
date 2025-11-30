# LocalDoorHub Android App

A kiosk-style Android tablet app for LocalDoorHub doorbell system.

## Features

- Splash screen with branding
- 5-step onboarding wizard
- Live video view (placeholder)
- Recent events timeline
- Full events list
- Status monitoring (battery, Wi-Fi)

## Setup

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on an Android tablet or emulator

## Configuration

### Mock API Server

To test the app with the mock API server:

1. Navigate to `mock-api/` directory
2. Run `npm install`
3. Start the server: `npm start`
4. Find your computer's IP address on the local network
5. Update `baseUrl` in `DoorbellRepository.kt` to `http://YOUR_IP:3001`

### Network Configuration

The app expects to connect to a local API at `http://192.168.8.1` by default. For development with the mock server, update the base URL as described above.

## Project Structure

- `MainActivity.kt` - Entry point with navigation
- `ui/` - All Compose screens
- `data/` - Repository and data models
- `theme/` - Material3 theme configuration

## Building

```bash
./gradlew assembleDebug
```

## Testing

The app includes:
- Onboarding flow with API integration
- Home screen with live view and events
- Events screen with full event list
- Mock API server for development

