# BillBot Android

A native Android companion app for [BillBot](https://github.com/oogleyskr/billbot) -- a self-hosted AI assistant powered by a 120B parameter model running on an NVIDIA DGX Spark. BillBot Android connects over Tailscale via WebSocket to the OpenClaw gateway, providing real-time streaming chat, hardware monitoring for a multi-GPU infrastructure, token usage analytics, and full remote configuration management.

Built with Kotlin, Jetpack Compose, and Material Design 3 with a dark theme.

## Features

### Chat
- Real-time streaming chat over WebSocket with token-by-token display
- Reasoning/thinking block display with collapsible sections
- Tool call tracking -- see which tools the model invokes and their results
- Session drawer: create, switch, rename, delete, and compact sessions
- Image attachments via camera or gallery with automatic compression
- Local message persistence via Room DB (survives app restarts)
- Abort in-flight responses

### Dashboard
- Live hardware monitoring for 3 GPUs:
  - **NVIDIA DGX Spark (GB10)** -- primary inference GPU
  - **NVIDIA RTX 3090** -- multimodal services
  - **AMD Radeon VII** -- memory cortex LLM
- Two views: **Devices** (per-card detail) and **Metrics** (cross-device comparison)
- Temperature, utilization, power draw, VRAM usage
- Service status indicators

### Token Counter
- All-time cumulative token usage, broken down by device
- Live inference speed (tokens/sec) per device
- Input vs. output token breakdown with visual bars

### Settings
- Dynamic configuration editor generated from JSON schema
- Log viewer with color-coded severity levels and text filtering
- Connection management and auth mode selection

### Background Service
- `BillBotService` foreground service keeps the WebSocket connection alive
- 3 notification channels (connection status, chat, system)
- Auto-reconnect with exponential backoff

### Authentication
Three supported auth modes:
- **Tailscale** (default) -- zero-config auth over Tailscale network
- **Token** -- bearer token authentication
- **Password** -- username/password credentials

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  Jetpack Compose screens + ViewModels (MVVM)    │
│  Chat │ Dashboard │ Tokens │ Settings │ Logs    │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────┐
│               Data Layer                         │
│  Repositories + SessionManager + UserPreferences │
└──────┬───────────────┬──────────────────────────┘
       │               │
┌──────┴──────┐  ┌─────┴─────────────────────┐
│  Room DB    │  │  GatewayClient (OkHttp)   │
│  Messages   │  │  WebSocket ⇄ OpenClaw GW  │
│  Sessions   │  │  JSON frames via           │
│             │  │  kotlinx.serialization     │
└─────────────┘  └───────────────────────────┘
```

- **MVVM** with Hilt dependency injection
- **GatewayClient** manages the WebSocket lifecycle, frame parsing, and reconnection
- **Room v2** database with DAOs for messages and sessions
- **DataStore Preferences** for connection settings and user preferences
- **Coil 3** for async image loading (attachments, generated images)
- **Navigation Compose** for tab-based navigation with a bottom bar

## Screenshots

> Screenshots coming soon.

## Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17** (install via [SDKMAN](https://sdkman.io/) if building from command line)
- **Android SDK** with compileSdk 35
- A running [BillBot/OpenClaw](https://github.com/oogleyskr/billbot) gateway instance
- [Tailscale](https://tailscale.com/) installed on both the Android device and the server (for default auth mode)

### Build

**Android Studio:**
1. Clone the repository:
   ```bash
   git clone https://github.com/oogleyskr/billbot-android.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and run on a device or emulator (API 26+).

**Command line (Linux/WSL):**
```bash
# Install JDK 17 via SDKMAN if needed
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh
sdk install java 17.0.13-tem

# Build debug APK
cd billbot-android
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration

On first launch, the app presents a connection screen. Enter:

| Field | Default | Description |
|-------|---------|-------------|
| Host | `100.96.181.60` | Tailscale IP or hostname of the OpenClaw gateway |
| Port | `18789` | Gateway WebSocket port |
| Auth Mode | Tailscale | One of: Tailscale, Token, or Password |

The connection is established over `ws://<host>:<port>` using the selected auth mode. All settings are persisted locally via DataStore Preferences.

## Tech Stack

| Category | Library | Version |
|----------|---------|---------|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose (BOM) | 2024.11 |
| Design | Material Design 3 | via Compose BOM |
| DI | Hilt | 2.52 |
| Networking | OkHttp | 4.12.0 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Database | Room | 2.6.1 |
| Image Loading | Coil | 3.0.4 |
| Navigation | Navigation Compose | 2.8.4 |
| Lifecycle | Lifecycle (runtime, viewmodel, process) | 2.8.7 |
| Preferences | DataStore | 1.1.1 |
| Coroutines | kotlinx.coroutines | 1.9.0 |
| Build | AGP | 8.7.3 |
| Annotation Processing | KSP | 2.0.21-1.0.28 |

**SDK targets:** compileSdk 35 / minSdk 26 (Android 8.0) / targetSdk 34

## License

This project is licensed under the [MIT License](LICENSE).
