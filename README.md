# Spotivibe

> **Code. Vibe. Sing along.**

Android native companion untuk Spotify — synced lyrics dengan romanization (JP / KR / ZH), notification dengan baris aktif, dan floating overlay melayang di atas app lain. Cocok untuk yang coding sambil dengerin K-pop / J-pop / Mandarin.

> Not affiliated with Spotify AB.

## Fitur

- **Now Playing** dengan album cover, judul, artist, slider seek (Premium-only)
- **Synced lyrics** dari [LRCLIB](https://lrclib.net) — auto-highlight baris aktif yang ter-sync ke playback. Tap baris untuk seek.
- **Romanization** otomatis per bahasa (toggle on/off):
  - Jepang (kana + kanji) → Romaji Hepburn via [kuromoji-ipadic](https://github.com/atilika/kuromoji)
  - Korea (Hangul) → Revised Romanization (port internal dari rules pemerintah)
  - Mandarin (Hanzi) → Pinyin dengan tone marks via [pinyin4j](https://github.com/belerweb/pinyin4j)
  - Tombol toggle cuma muncul saat lirik mengandung script non-Latin
- **Notification dengan synced lyrics** — baris prev/current/next karaoke-style di pull-down + lock screen
- **Floating overlay** full-width, draggable Y, di atas app lain — mini bar (collapsed) ↔ expanded card (tap-to-expand)
- **Dynamic accent color** — Palette API extract dari album cover (clamped HSV agar readable di dark theme)
- **Auth persistence** — connect sekali, auto-reconnect di session berikutnya
- **Disk-based lyrics cache** — 30 hari positive / 1 hari negative TTL, LRU memory cache bounded 50 entries
- **Glassmorphism Tokyo** theme — dark navy gradient + frosted surface

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Spotify App Remote SDK + Auth SDK (distribusi AAR di `app/libs/`)
- LRCLIB API via Retrofit + Moshi
- Room (gak dipakai — JSON file cache lebih simple)
- DataStore Preferences untuk persist toggle
- Coroutines + StateFlow reactive
- Palette API untuk dominant color
- Manual DI via Application container (no Hilt — cold start sensitive)

## Setup

### Prerequisites

- Android Studio (Hedgehog atau lebih baru)
- Android device fisik (overlay + Spotify integration butuh real device, bukan emulator)
- Spotify app ter-install di device, login dengan akun **Premium** (Premium required untuk seek/play/pause via App Remote)

### 1. Clone

```bash
git clone git@github-personal:tektungg/spotivibe.git
cd spotivibe
```

### 2. Spotify Developer setup

1. Buka https://developer.spotify.com/dashboard
2. **Create app** dengan:
   - **Redirect URI**: `spotivibe://callback`
   - **APIs used**: centang **Web API** + **Android**
3. Catat **Client ID**

### 3. Generate debug keystore SHA-1, register di Spotify Dashboard

```powershell
# Cari keytool di JBR Android Studio
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"

# Print debug keystore SHA-1
& $keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Copy SHA-1 (format `XX:XX:XX:...`), tambahkan ke Spotify Dashboard → app → Settings → Android Packages:
- Package name: `com.tglabs.spotivibe`
- SHA-1: yang barusan di-copy

### 4. Configure local.properties

Buat / edit `local.properties` di project root:

```properties
sdk.dir=C\:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
spotify.clientId=YOUR_SPOTIFY_CLIENT_ID
```

### 5. Build & run

Di Android Studio → Sync Gradle → klik ▶ Run (pilih device fisik).

## Build release APK

Lihat detail di [docs/release-build.md](docs/release-build.md) (TODO). Quick:

```powershell
# Generate keystore (one-time)
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias spotivibe

# Buat keystore.properties (lihat keystore.properties.example)

# Build release APK
.\gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

> Release keystore SHA-1 beda dari debug — daftar juga di Spotify Dashboard kalau mau App Remote jalan di release build.

## Architecture

```
SpotivibeApp (Application)
├── applicationScope (SupervisorJob + Main.immediate)
├── preferencesRepository (DataStore)
├── spotifyConnection ─ AppRemote IPC + Auth
├── lyricsRepository ─ LRCLIB + 3-layer cache (mem LRU 50, disk JSON, network)
├── romanizationService ─ JA/KR/ZH lazy-init
└── playbackController ─ orchestrator
        ├── observe nowPlaying (Spotify event stream)
        ├── fetch lyrics on track change
        ├── 500ms tick → extrapolate currentLine
        ├── compute romaji reactive (off main)
        └── compute accent (Palette, off main)
                ↓
        ┌───────┴─────────┬──────────────────┐
        ▼                 ▼                  ▼
ViewModel + UI      NotificationService    OverlayManager
(NowPlayingScreen)  (foreground service)   (WindowManager + ComposeView)
```

## Permissions

- `INTERNET` — LRCLIB fetch
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — notification service hidup di background
- `POST_NOTIFICATIONS` — Android 13+ runtime
- `SYSTEM_ALERT_WINDOW` — floating overlay (granted via Settings)

## Tested on

- POCO X7 Pro / Xiaomi 2412DPC0AG, HyperOS 3.0.3 (Android 14+)
- Pixel 9 Pro (emulator)

## Compatibility caveats

- **Spotify Premium required** untuk play/pause/seek/next/prev via App Remote API
- **Min SDK 26** (Android 8) — covers ~95% device global
- **HyperOS Dynamic Island** tidak di-hijack karena pakai `foregroundServiceType=specialUse` (bukan `mediaPlayback`)
- **MIUI Autostart + Battery No Restrictions** untuk Spotivibe + Spotify wajib dinyalakan biar service stabil

## License

MIT (rencananya) — TODO file `LICENSE`.

## Credits

- Spotify SDK © Spotify AB
- LRCLIB.net — community lyrics database
- kuromoji © Atilika Inc.
- pinyin4j © Li Min
- Korean Revised Romanization port internal
