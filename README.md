# Spotify Controller for Android 🎵

A high-performance Android remote controller for Spotify built with modern **Jetpack Compose**, **Material 3 Dynamic Tonal Spot Theming**, **Album Art Mesh Gradient Backdrops**, and a port of Spicetify's **Spicy Lyrics** engine for live time-synced karaoke lyrics.

---

## ✨ Features

- **Rich Now Playing Display**: High-resolution Album Art, Title (with smooth marquee for long titles), Artist(s), Album name, and Active Device status indicator.
- **Full Playback Controls**:
  - Play / Pause with animated icon transitions
  - Next track & Previous track
  - Like / Favorite toggle (saves to / removes from your Spotify library with spring bounce animation)
  - Shuffle mode toggle with active indicator
  - Repeat mode cycle: **Off** ➔ **Repeat Context (All)** ➔ **Repeat Track (One)**
  - Interactive Progress Bar: Continuous client-side timer interpolation + draggable seek bar with formatted `mm:ss` timestamps
  - Volume Bar: Continuous volume slider with speaker mute toggle and volume up step
- **Album Art Gradient Background**:
  - Automatically samples dominant, vibrant, and dark muted hues from the active album cover using AndroidX Palette.
  - Multi-stop canvas radial & vertical falloff gradient that dynamically shifts and crossfades on track changes.
- **Material 3 Tonal Spot Theming**:
  - Dynamically synthesizes an M3 Dark ColorScheme directly from the album art seed color.
  - Buttons follow the tonal palette: Primary FAB for Play/Pause, Secondary Container for Next/Previous, Tonal Surface for controls, and primary accents for sliders.
- **Spicy Lyrics (Ported from Spicetify)**:
  - **Tap-to-Lyrics**: Tapping the album art flips straight into the live lyrics screen.
  - **LRCLIB Integration**: Queries the free, public, high-coverage LRCLIB database (the primary engine behind Spicy Lyrics) with no API keys required.
  - **Synchronized Auto-Scroll**: Highlights the active line with large bold typography and glowing tonal accent, while non-active lines fade gracefully. The view auto-centers as the song progresses.
  - **Interactive Tap-to-Seek**: Tapping any lyric line immediately jumps playback on Spotify to that exact timestamp!
- **Dual Landscape & Portrait Modes**:
  - **Portrait Mode**: Optimized for handheld phone usage.
  - **Landscape Mode**: Dual-pane split layout ideal for tablet mounts, car head units, or bedside desk displays.
  - **Orientation Switcher Button**: Dedicated button in the top action bar to toggle between Landscape and Portrait orientation on demand.
- **Spotify Developer Platform Integration**:
  - First-run guided setup dialog.
  - Connects using secure OAuth 2.0 Authorization Code Flow with PKCE (Proof Key for Code Exchange).
  - Also supports optional direct access tokens for rapid developer testing.
  - "Test Connection" button to verify credentials instantly against `/v1/me`.

---

## 🛠️ Tech Stack & Architecture

- **UI**: 100% Jetpack Compose with Material 3
- **Language**: Kotlin 2.0 with Coroutines & StateFlow
- **Image & Color Processing**: Coil Compose + AndroidX Palette
- **Network**: OkHttp 4.12.0
- **Storage**: Jetpack DataStore Preferences
- **Architecture**: MVVM with unidirectional data flow (UDF)

```
app/src/main/java/com/spotify/controller/
├── MainActivity.kt                      // Edge-to-edge Activity & deep-link receiver
├── SpotifyControllerApp.kt              // Application dependency container
├── data/
│   ├── model/
│   │   ├── Track.kt                     // Track domain model
│   │   ├── PlaybackState.kt             // Playback state + monotonic progress calculation
│   │   ├── RepeatMode.kt                // Off, Context, Track
│   │   ├── Lyrics.kt & LyricLine.kt     // Line and word-timing data structures
│   │   └── SpotifyCredentials.kt        // Token & Client ID storage model
│   ├── remote/
│   │   ├── SpotifyApiService.kt         // Spotify Web API endpoints
│   │   ├── SpotifyAuthManager.kt        // PKCE OAuth 2.0 handshake
│   │   └── LrclibApiService.kt          // LRCLIB lyrics query & search API
│   └── repository/
│       ├── SpotifyRepository.kt         // Polling, optimistic controls, state flow
│       ├── LyricsRepository.kt          // Lyrics caching & LRCLIB bridge
│       ├── CredentialsRepository.kt     // DataStore preferences
│       └── LrcParser.kt                 // Timestamp parser [mm:ss.xx]
└── ui/
    ├── components/
    │   ├── AlbumArtCard.kt              // Album art with tap-to-lyrics cue
    │   ├── SpicyLyricsView.kt           // Ported Spicy Lyrics synced view
    │   ├── PlayerControls.kt            // M3 tonal buttons & like button
    │   ├── ProgressBar.kt               // Continuous seek bar & timers
    │   ├── VolumeBar.kt                 // Volume slider & mute control
    │   └── DynamicGradientBackground.kt // Multi-stop album art gradient
    ├── screens/
    │   ├── MainPlayerScreen.kt          // Screen coordinator
    │   ├── PortraitPlayerLayout.kt      // Portrait layout
    │   ├── LandscapePlayerLayout.kt     // Landscape dual-pane layout
    │   └── SetupScreen.kt               // Spotify Dev onboarding & test dialog
    ├── theme/
    │   ├── Color.kt & Type.kt           // Base styling & typography
    │   ├── Theme.kt                     // MaterialTheme wrapper
    │   └── PaletteExtractor.kt          // AndroidX Palette -> M3 Tonal Spot synthesizer
    └── viewmodel/
        ├── PlayerViewModel.kt           // Playback, theme, orientation coordinator
        └── LyricsViewModel.kt           // Lyrics sync and state
```

---

## 🚀 Getting Started

### 1. Spotify Developer Platform Setup

1. Log in to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Click **Create App**:
   - **App name**: `Spotify Controller`
   - **App description**: `Remote playback controller with synced lyrics`
   - **Redirect URI**: Add:
     ```
     spotifycontroller://callback
     ```
   - Which API/SDKs are you planning to use? Select **Web API**.
3. Save and go to **Settings** to find your **Client ID**.

### 2. Building the Project

Open the project in **Android Studio** (Koala / Ladybug or newer):
1. Allow Gradle to sync dependencies.
2. Select your device or emulator (Android 8.0 / API 26+).
3. Run the application (`Run 'app'`).

### 3. Connecting

1. On first launch, the **Spotify Setup** screen will appear.
2. Paste your **Client ID**.
3. Tap **Authorize with Spotify Login**:
   - A Chrome Custom Tab will open with the official Spotify authorization dialog.
   - Authorize the app, and you will be returned directly back to the controller.
4. Start playing music on any Spotify device (Phone, Desktop, Echo, Smart TV, Web Player), and the controller will instantly display the album art, lyrics, and controls!
