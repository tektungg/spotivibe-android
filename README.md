# Spotivibe

[![CI](https://github.com/tektungg/spotivibe-android/actions/workflows/ci.yml/badge.svg)](https://github.com/tektungg/spotivibe-android/actions/workflows/ci.yml)

> **Code. Vibe. Sing along.**

Android native companion untuk Spotify — synced lyrics dengan romanization (JP / KR / ZH), notification dengan baris aktif, dan floating overlay melayang di atas app lain. Cocok untuk yang coding sambil dengerin K-pop / J-pop / Mandarin.

> Not affiliated with Spotify AB.

## Fitur

- **Now Playing** dengan album cover, judul, artist, slider seek (butuh Premium untuk kontrol)
- **Synced lyrics** dari [LRCLIB](https://lrclib.net) — auto-highlight baris aktif yang ter-sync ke playback. Tap baris untuk seek.
- **Romanization** otomatis per bahasa (toggle on/off):
  - Jepang (kana + kanji) → Romaji Hepburn via [kuromoji-ipadic](https://github.com/atilika/kuromoji)
  - Korea (Hangul) → Revised Romanization (port internal dari rules pemerintah)
  - Mandarin (Hanzi) → Pinyin dengan tone marks via [pinyin4j](https://github.com/belerweb/pinyin4j)
  - Tombol toggle cuma muncul saat lirik mengandung script non-Latin **yang
    didukung varian ini**. Varian `lite` tidak membawa kamus Jepang, jadi
    tombolnya tidak muncul untuk lagu Jepang di sana
  - Script ditentukan **sekali per lagu**, bukan per baris. Kana eksklusif milik
    Jepang dan hangul eksklusif milik Korea, tapi kanji dipakai bersama Jepang
    dan Mandarin. Kalau diputuskan per baris, baris Jepang yang isinya kanji saja
    keluar sebagai pinyin di tengah lagu yang selebihnya romaji
- **Notification dengan synced lyrics** — baris prev/current/next karaoke-style di pull-down + lock screen
- **Floating overlay** full-width, draggable Y, di atas app lain — mini bar (collapsed) ↔ expanded card (tap-to-expand)
- **Dynamic accent color** — Palette API extract dari album cover (clamped HSV agar readable di dark theme)
- **Auth OAuth 2.0 Authorization Code + PKCE** — connect sekali, sesi hidup terus lewat refresh token. Tidak ada client secret di APK
- **Disk-based lyrics cache** — 30 hari positive / 1 hari negative TTL, LRU memory cache bounded 50 entries
- **Glassmorphism Tokyo** theme — dark navy gradient + frosted surface

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Spotify App Remote SDK (distribusi AAR di `app/libs/`)
- OAuth Authorization Code + PKCE via Custom Tabs (`androidx.browser`)
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

Sekali saja, untuk menyiapkan keystore:

```powershell
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias spotivibe
# lalu buat keystore.properties, lihat keystore.properties.example
```

Setiap rilis, jalankan script-nya. Jangan `assembleFullRelease` langsung:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\release.ps1
```

Script yang menjalankan test kedua varian, build keduanya, lalu menaruh hasilnya
di `dist/`.
Nama berkas dan folder tujuan diturunkan dari `versionName` di `app/build.gradle.kts`,
bukan diketik ulang, karena langkah salin manual sudah pernah salah tujuan sekali.
Script juga mencari JDK Android Studio sendiri kalau `JAVA_HOME` belum diset, dan
menolak jalan kalau `keystore.properties` belum ada, supaya build tidak diam-diam
jatuh ke debug signing.

`-SkipTests` melewati test, hanya untuk build ulang cepat, bukan untuk rilis.

`dist/` masuk `.gitignore` (`*.apk`). APK tidak pernah di-commit.

## Dua varian APK

| Varian | Jepang | Korea | Mandarin | Ukuran |
|---|---|---|---|---|
| `full` | ya | ya | ya | ~15,8 MB |
| `lite` | tidak | ya | ya | ~3 MB |

Bedanya **hanya** dukungan romanisasi Jepang. Kamus IPADIC milik kuromoji
mengisi 12,71 dari 15,75 MB APK, yaitu 81% ukurannya untuk satu bahasa. Kanji
tidak bisa diromanisasi tanpa kamus morfologis, jadi tidak ada versi ringan dari
kemampuan itu: pilihannya memuat kamusnya atau tidak mendukung Jepang.

Korea dan Mandarin ada di **kedua** varian. Hangul dibaca per suku kata tanpa
kamus, dan tabel pinyin4j cuma 0,21 MB.

Play Feature Delivery tidak dipakai karena distribusinya lewat GitHub Releases,
bukan Play Store, dan modul on-demand butuh Play.

Pemisahannya bergantung pada satu syarat: tidak ada kode di `src/main/` yang
menyebut `com.atilika.kuromoji`. Satu import saja membuat varian `lite` gagal
compile. `FlavorGuardTest` memindai sumbernya dan menangkap itu lebih awal,
sekaligus memastikan tiga deklarasi per-varian tetap sepakat satu sama lain.

applicationId keduanya **sama**, jadi yang satu menimpa yang lain saat dipasang.
Itu disengaja: dua app terpasang berbarengan akan berebut redirect auth
`spotivibe://callback`.

## Rilis otomatis lewat GitHub Actions

Tiga workflow di `.github/workflows/`:

| Workflow | Pemicu | Hasil |
|---|---|---|
| `ci.yml` | push ke `main`, PR | unit test kedua varian + lint. Tanpa secret, di bawah semenit |
| `ui-tests.yml` | PR, harian, manual | test instrumentasi Compose di emulator API 26 dan 34 |
| `release.yml` | tag `v*` | dua APK ditandatangani, terpasang di GitHub Release |

Test instrumentasi sengaja **tidak** ikut di `ci.yml`. Emulator butuh menit,
bukan detik, dan gate yang lambat pelan-pelan berhenti dibaca orang.

### Sekali saja: pasang secret

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-ci-secrets.ps1
```

Script membaca `keystore.properties`, `local.properties`, dan `release.jks` lalu
mengirim lima secret lewat `gh`. Nilainya tidak pernah ditampilkan dan tidak perlu
diketik ulang, jadi password tidak singgah di clipboard maupun riwayat shell.
Repo tujuan diambil dari remote git, bukan diketik. Tambahkan `-WhatIf` untuk
melihat apa yang akan dikirim tanpa mengirim apa pun.

Secret yang dipasang: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`, `SPOTIFY_CLIENT_ID`.

### Setiap rilis

```bash
git tag v0.6.0 && git push origin v0.6.0
```

CI membangun APK dengan keystore yang **sama** dengan build lokal, jadi bisa
dipasang menimpa versi sebelumnya dan SHA-1-nya tetap yang terdaftar di Spotify
Dashboard.

Tiga hal yang ditolak workflow di depan, karena ketiganya gagal secara diam-diam
kalau dibiarkan:

- **Tag tidak cocok `versionName`.** Tag `v0.7.0` di atas `versionName = "0.6.0"`
  akan menghasilkan APK bernama salah yang baru ketahuan saat gagal dipasang.
- **`KEYSTORE_BASE64` rusak.** Base64 cacat menghasilkan `release.jks` mungil,
  dan Gradle gagal jauh setelahnya dengan pesan yang tidak menyebut base64.
- **APK bertanda tangan debug.** Kalau `keystore.properties` gagal terbaca,
  Gradle jatuh ke debug key dan build tetap hijau. `apksigner verify` memeriksanya.

Menjalankan `release.yml` lewat "Run workflow" tanpa tag tetap menghasilkan APK
sebagai artifact, hanya tidak membuat Release.

> Release keystore SHA-1 beda dari debug — daftar juga di Spotify Dashboard kalau mau App Remote jalan di release build.

## Architecture

### Auth

Login memakai **Authorization Code + PKCE** lewat Custom Tab ke
`accounts.spotify.com`, bukan `AuthorizationClient` dari Auth SDK.

Alasannya struktural, bukan preferensi. Auth SDK cuma bisa memberi implicit
grant (`Type.TOKEN`) di jalur native, dan implicit grant tidak punya refresh
token, jadi sesi mati setelah satu jam. Jalur `Type.CODE` +
`setCustomParam("code_challenge", …)` juga tidak jalan: `toUri()` (jalur
browser) memang meneruskan custom param, tapi
`SpotifyNativeAuthUtil.startAuthActivity()` cuma mengirim
`VERSION, CLIENT_ID, REDIRECT_URI, RESPONSE_TYPE, SCOPES, STATE` ke intent dan
membuang sisanya. Karena app ini mensyaratkan Spotify terpasang, jalur native
selalu menang dan `code_challenge` tidak pernah sampai.

Alurnya:

```
ConnectScreen tap
  └─ authRepository.beginAuthorization()   generate verifier + challenge + state, persist
       └─ Custom Tab → accounts.spotify.com/authorize?...&code_challenge=…
            └─ redirect spotivibe://callback?code=…&state=…
                 └─ MainActivity.onNewIntent (launchMode=singleTask)
                      └─ POST /api/token (code + code_verifier, tanpa client secret)
                           └─ access + refresh token → DataStore
                                └─ SpotifyAppRemote.connect(showAuthView=false)
```

**Kuirk yang sudah terkonfirmasi di device:** Spotify memantulkan redirect
dengan garis miring di akhir walaupun yang didaftarkan tanpa itu, dan
menambahkan parameter `ubi` yang tidak diminta:

```
didaftarkan  spotivibe://callback
dipantulkan  spotivibe://callback/?code=…&state=…&ubi=…
```

Karena itu perbandingan base URI dinormalkan lebih dulu, bukan `String.equals`
persis. Parameter asing diabaikan.

Semua pembacaan token lewat `authRepository.validAccessToken()`, yang me-refresh
sendiri 60 detik sebelum expiry di bawah satu mutex, jadi pemanggil bersamaan
(UI, notification service, overlay, preload antrean) hanya memicu satu request
refresh. Kegagalan sementara (jaringan) mempertahankan kredensial; hanya
penolakan permanen (`invalid_grant` dan kawan-kawan) yang memaksa login ulang.

Token disimpan di DataStore dan direktori `datastore/` dikecualikan dari cloud
backup maupun device transfer.

### Parsing LRC dan identitas baris

`SyncedLine` punya `id` (posisinya setelah diurutkan) yang terpisah dari
`timeMs`. Ini ada karena `timeMs` bukan identitas: file LRC boleh, dan sering,
punya beberapa baris dengan timestamp identik. Dulu `timeMs` dipakai sebagai
key `LazyColumn`, key map romaji, dan set seleksi sekaligus. Akibatnya Compose
melempar `IllegalArgumentException: Key was already used` pada lagu seperti itu,
dua baris saling menimpa romaji, dan memilih satu baris ikut memilih
kembarannya.

Format yang didukung:

- Standar `[01:23.45]teks`
- **Multi-timestamp** `[00:12.00][01:30.00]Reff`, satu teks di beberapa waktu.
  Versi lama memakai `Regex.find` yang cuma mengambil kecocokan pertama, jadi
  timestamp kedua hilang DAN literal `[01:30.00]` ikut terbawa ke layar.
- Enhanced per-kata `<mm:ss.xx>`. Kalau barisnya multi-timestamp, timing
  per-kata dibuang: nilainya absolut jadi tidak bisa benar untuk lebih dari
  satu kemunculan.
- Tag metadata (`[ar:]`, `[ti:]`, dan lain-lain) dilewati.

`[offset:...]` dikenali dan dilewati supaya tidak bocor ke layar, tapi nilainya
sengaja **tidak** diterapkan. Arah tandanya berbeda antar pemutar, dan menebak
salah membuat sync lebih buruk daripada tidak mendukung sama sekali. Koreksi
manual di Settings sudah menutupi kebutuhan ini dan berlaku di semua permukaan.

Cache disk sekarang ikut menyimpan timing per-kata. Sebelumnya field itu hilang
saat round-trip, jadi karaoke per-kata jalan di pemutaran pertama lalu
diam-diam turun ke per-baris begitu cache dipakai.

### Siklus koneksi

`SpotifySessionSupervisor` hidup di application scope dan menjaga koneksi
selama masih ada sesi. Activity tidak lagi memilikinya.

Dulu `MainActivity` yang memanggil `tryAutoConnect` DAN yang menghidup-matikan
foreground service berdasarkan `connectionState`. Dua akibatnya buruk. Spotify
di-kill sekali berarti state jatuh ke Disconnected, service `stopSelf()`,
notification hilang, dan tidak ada apapun yang menyambung lagi sampai user
membuka app ini secara manual. Dan service yang dibangkitkan sistem lewat
START_STICKY hidup tanpa ada apapun yang menyambungkannya ke Spotify, jadi cuma
jadi notification kosong.

Yang masih dipegang Activity cuma satu, dan memang wajib: menyalakan foreground
service, karena Android 12+ melarang menyalakannya dari background.

Backoff reconnect: percobaan pertama langsung, lalu 1, 2, 4, 8 detik dan
seterusnya sampai batas 5 menit. Batasnya sengaja menit, bukan detik, karena
kegagalan beruntun biasanya berarti Spotify memang tidak jalan. Tidak ada
keadaan menyerah: selama sesi masih ada, companion terus mencoba, cuma makin
jarang. Satu-satunya cara berhenti adalah sesinya hilang lewat logout atau
otorisasi dicabut Spotify.

Putusnya link dideteksi lewat `Subscription.LifecycleCallback.onStop` dan error
callback pada subscription player state. Sebelumnya keduanya cuma di-log, jadi
koneksi bisa jadi zombie: state tetap Connected padahal tidak ada event yang
masuk lagi selamanya.

Saat sedang menunggu percobaan berikutnya, notification menampilkan
"Menyambungkan ulang ke Spotify…" supaya user tidak menatap lirik basi tanpa
tahu kenapa berhenti bergerak.

### Cache lirik

Tiga lapis: memory LRU 50, file JSON di `cacheDir`, lalu jaringan.

Lapis disk dibatasi **500 entry atau 8 MB**, mana yang lebih dulu tercapai, plus
buang otomatis apa pun yang lebih tua dari 30 hari. Sebelumnya tidak ada batas
sama sekali: file hanya terhapus kalau kebetulan dibaca lagi setelah
kedaluwarsa, jadi lagu yang diputar sekali lalu tidak pernah disentuh menetap
selamanya.

Pembuangannya LRU, bukan FIFO. Stempel waktu file di-sentuh ulang saat dibaca,
supaya lagu yang sering diputar bertahan lebih lama daripada yang ditulis
belakangan tapi tidak pernah dibuka lagi. `setLastModified` tidak selalu berhasil
di semua filesystem Android; kalau gagal, kebijakannya merosot jadi FIFO, yang
masih terbatas dan tetap benar.

Sapuan berjalan sekali per 25 penulisan, bukan tiap penulisan, supaya tidak
melisting seluruh direktori setiap ganti lagu.

Yang masuk cache HANYA jawaban sungguhan:

| Hasil lookup | Ditulis? | TTL |
|---|---|---|
| Ada lirik | ya | 30 hari |
| LRCLIB memastikan tidak ada | ya, penanda kosong | 1 hari |
| Gagal dijangkau (jaringan, timeout, 5xx, 429) | **tidak sama sekali** | — |

Dulu semua exception ditelan jadi satu hasil kosong yang tetap ditulis ke disk
dengan TTL negatif. Satu lagu yang kebetulan diputar saat sinyal hilang
kehilangan liriknya 24 jam penuh, walaupun jaringan balik semenit kemudian.

`/get` dan `/search` ditanya paralel, lalu digabung. Aturan yang menentukan:
**"tidak ada" hanya berlaku kalau KEDUA probe berhasil dan sama-sama bilang
tidak ada.** Kalau salah satunya gagal dijangkau, kita tidak benar-benar tahu,
karena yang gagal itu mungkin justru punya liriknya. 404 dari `/get` dihitung
sebagai jawaban sungguhan; 429 dan 5xx tidak.

Kegagalan sementara diulang sampai 3 kali dengan backoff 400 ms, 800 ms, 1.6 s.
Preload antrean selalu satu percobaan saja, supaya tidak berebut jaringan
dengan lagu yang sedang diputar.

UI membedakan keduanya: "Lirik tidak ditemukan untuk track ini" versus "Gagal
memuat lirik. Cek koneksi, lalu putar ulang lagunya."

### Statistik lirik

Metrik inti produk ini: **berapa persen lagu yang diputar dapat lirik ter-sync.**
Terlihat di Settings, bukan cuma tercatat, karena angka yang tidak bisa dilihat
sama saja tidak ada.

Keputusan yang menentukan ada di penyebutnya. Lookup yang **gagal dijangkau**
dikeluarkan dari penyebut coverage, karena jaringan mati itu kegagalan kita
menjangkau, bukan lubang di database LRCLIB. Kalau ikut dihitung, angka coverage
turun setiap kali sinyal jelek dan kita akan menyimpulkan kualitas pencarian
memburuk padahal yang terjadi cuma masuk lift.

| Angka | Artinya |
|---|---|
| `SYNCED` | dari yang terjawab, berapa persen dapat lirik ter-sync |
| `ANY LYRICS` | termasuk yang cuma teks polos |
| `REACHED` | seberapa sering LRCLIB berhasil dijangkau sama sekali |
| `FROM CACHE` | seberapa sering jawaban datang tanpa menyentuh jaringan |
| `SEARCH ONLY` | dari lookup jaringan yang berhasil, berapa yang cuma bisa dijawab `/search` |

`SEARCH ONLY` adalah angka yang menentukan pekerjaan berikutnya. Sekarang
`/get` dan `/search` selalu ditembak paralel, yang menggandakan beban ke API
komunitas gratis. Kalau angka itu ternyata rendah, `/search` bisa diturunkan
jadi fallback saat `/get` meleset.

Rate yang belum punya data ditulis sebagai tanda hubung, bukan 0%, supaya
"belum tahu" tidak tersamar jadi "buruk".

### Sync lirik

Baris aktif dihitung di SATU tempat: `LyricsSyncEngine`, dipegang oleh
`PlaybackController` dan diterbitkan sebagai `currentLineIndex`. Layar utama,
notification, dan overlay semuanya membaca angka itu. UI tidak boleh
menghitungnya lagi dari progress.

Dulu logika ini ada dua salinan identik, di `PlaybackController` (tick 500 ms,
dipakai notification + overlay) dan di `LyricsList` (tick 200 ms, dipakai
layar). Akibatnya dua clock berbeda, dan yang lebih buruk: `lyricsOffsetMs`
hanya diterapkan di salinan milik UI, jadi setting offset yang dikira global
sebenarnya cuma menggeser satu dari tiga permukaan.

Yang masih dihitung lokal di UI cuma dua hal, dan keduanya memang murni
tampilan: posisi slider transport (pakai posisi playback ASLI, tanpa offset)
dan highlight per-kata untuk LRC enhanced.

Ticker-nya adaptif, bukan interval tetap. Engine menghitung jarak tepat ke
batas baris berikutnya lalu tidur selama itu, dipotong ke rentang 16 sampai
250 ms. Interval tetap salah di dua arah sekaligus: terlalu jarang saat lagu
jalan sehingga baris telat menyala, dan terlalu sering saat pause atau saat
lagu tidak punya lirik sehingga membangunkan CPU tanpa hasil.

Tap baris untuk seek mengompensasi offset (`seekTargetMs`). Tanpa itu, dengan
offset non-nol tap baris mendarat di posisi yang justru membuat baris
berikutnya yang menyala.

### Container

```
SpotivibeApp (Application)
├── applicationScope (SupervisorJob + Main.immediate)
├── preferencesRepository (DataStore, sekaligus AuthStorage)
├── authRepository ─ PKCE + token refresh (single-flight)
├── spotifyConnection ─ AppRemote IPC
├── lyricsRepository ─ LRCLIB + 3-layer cache (mem LRU 50, disk JSON, network)
├── romanizationService ─ JA/KR/ZH lazy-init
└── playbackController ─ orchestrator
        ├── observe nowPlaying (Spotify event stream)
        ├── fetch lyrics on track change
        ├── lyricsSyncEngine → currentLineIndex (tick adaptif, offset-aware)
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

- **Spotify Premium required** untuk play/pause/seek/next/prev via App Remote API.
  Kontrol ditampilkan optimistis selama tipe akun belum terkonfirmasi, dan baru
  disembunyikan setelah ada bukti nyata: `/me` bilang non-premium, atau App
  Remote menolak perintah. Panggilan Web API yang gagal TIDAK menyembunyikan
  kontrol, karena kontrolnya sendiri jalan lewat App Remote
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
