# Spotivibe

[![CI](https://github.com/tektungg/spotivibe-android/actions/workflows/ci.yml/badge.svg)](https://github.com/tektungg/spotivibe-android/actions/workflows/ci.yml)

> **Code. Vibe. Sing along.**

Android native companion untuk player musik apa pun — Spotify, YouTube Music, atau pemutar lain. Synced lyrics dengan romanization (JP / KR / ZH), notification dengan baris aktif, dan floating overlay melayang di atas app lain. Tanpa login: cukup satu izin sistem. Cocok untuk yang coding sambil dengerin K-pop / J-pop / Mandarin.

> Not affiliated with Spotify AB.

## Fitur

- **Ikuti player apa pun** lewat MediaSession Android: Spotify (termasuk Free),
  YouTube Music, dan pemutar lain. Tidak ada akun yang perlu login; app hanya
  minta izin notification access. Pindah otomatis ke app yang mulai memutar.
  Detail di [Sumber playback](#sumber-playback)
- **Now Playing** dengan album cover, judul, artist, slider seek. Tombol mengikuti
  apa yang diizinkan player itu sendiri, bukan status akun
- **Gestur cover**: geser cover (atau thumbnail + judul di portrait) ke kiri untuk
  lagu berikutnya, ke kanan untuk sebelumnya; ketuk dua kali untuk play/pause.
  Ambang 48 dp, dan gerakan yang tidak diizinkan player diabaikan
  (`domain/CoverGesture.kt`)
- **Synced lyrics dari empat sumber yang berlomba** — [LRCLIB](https://lrclib.net),
  [AMLL TTML DB](https://amll.dev), [lrc.red](https://lrc.red), dan
  [LRCMux](https://lrcmux.dev). Yang timing-nya paling halus (per kata > per
  baris > teks polos) menang. Tap baris untuk seek. Detail di
  [Sumber lirik](#sumber-lirik)
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
- **Sync offset tiga lapis** — global, per perangkat output (mobil, earbud,
  speaker HP masing-masing punya jedanya sendiri), dan per lagu lewat menu
  "Sync this song". Detail di [Sync lirik](#sync-lirik)
- **Karaoke per suku kata** — di baris aktif warna menyapu tiap suku kata dari
  kiri, kata sedikit membesar, terangkat, dan bercahaya saat dinyanyikan, lalu
  memantul kembali lewat pegas. Kata yang ditahan >= 1 detik menyapu per huruf.
  Settings → **Lyric effects** mematikannya untuk head unit yang lemah. Detail
  di [Animasi karaoke](#animasi-karaoke)
- **Blur menurut jarak dan fade di tepi** — baris non-aktif makin kabur makin
  jauh dari baris aktif (1,25 dp per baris, maksimal 6,83 dp), dan tepi atas/bawah
  daftar memudar. Blur lepas saat user menggulir atau memilih baris, mati di high
  contrast, dan tidak berefek di Android < 12 (head unit tampil tajam)
- **Titik-titik jeda instrumental** — intro dan interlude >= 3 detik menampilkan
  tiga titik yang menyala bergantian lalu menyusut tepat sebelum baris
  berikutnya (`domain/Interlude.kt`), menggantikan "♪" yang diam
- **Notification dengan synced lyrics** — baris prev/current/next karaoke-style di pull-down + lock screen
- **Floating overlay** full-width, draggable Y, di atas app lain — mini bar (collapsed) ↔ expanded card (tap-to-expand)
- **Dynamic accent color** — Palette API extract dari album cover (clamped HSV agar readable di dark theme)
- **Disk-based lyrics cache** — 30 hari positive / 1 hari negative TTL, LRU memory cache bounded 50 entries
- **Offline mode** — lirik yang pernah dimuat tetap tampil tanpa internet berapa
  pun umurnya, nol panggilan jaringan saat offline, eyebrow header "OFFLINE",
  dan lirik yang gagal dimuat diambil sendiri begitu jaringan kembali. Detail di
  [Offline](#offline)
- **Glassmorphism Tokyo** theme — dark navy gradient + frosted surface

## Stack

- Kotlin + Jetpack Compose (Material 3)
- MediaSession Android (`MediaSessionManager` + NotificationListenerService kosong) untuk player apa pun
- LRCLIB via Retrofit + Moshi; AMLL, lrc.red, LRCMux via OkHttp
- Room (gak dipakai — JSON file cache lebih simple)
- DataStore Preferences untuk persist toggle
- Coroutines + StateFlow reactive
- Palette API untuk dominant color
- Manual DI via Application container (no Hilt — cold start sensitive)

## Setup

### Prerequisites

- Android Studio (Hedgehog atau lebih baru)
- Android device fisik (overlay + MediaSession player sungguhan butuh real device, bukan emulator)
- Player musik apa pun di device (Spotify, YouTube Music, ...). Tidak perlu akun atau Premium

### 1. Clone

```bash
git clone git@github-personal:tektungg/spotivibe.git
cd spotivibe
```

### 2. Build & run

Di Android Studio → Sync Gradle → klik ▶ Run (pilih device fisik). Tidak ada
client ID, secret, atau dashboard developer yang perlu disiapkan.

### 3. Beri izin notification access

Saat pertama dibuka, Spotivibe meminta **notification access**: Android hanya
mengizinkan app membaca lagu yang diputar app lain lewat izin itu. Tekan
**ALLOW NOTIFICATION ACCESS**, aktifkan Spotivibe, lalu kembali; layar izin
hilang sendiri.

Sebagian head unit mobil menyembunyikan layar Settings itu. Izinnya tetap bisa
diberikan lewat adb (Android 8.1+):

```bash
adb shell cmd notification allow_listener com.tglabs.spotivibe/com.tglabs.spotivibe.service.SessionAccessService
```

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

Script membaca `keystore.properties` dan `release.jks` lalu mengirim empat secret
lewat `gh`. Nilainya tidak pernah ditampilkan dan tidak perlu
diketik ulang, jadi password tidak singgah di clipboard maupun riwayat shell.
Repo tujuan diambil dari remote git, bukan diketik. Tambahkan `-WhatIf` untuk
melihat apa yang akan dikirim tanpa mengirim apa pun.

Secret yang dipasang: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Secret `SPOTIFY_CLIENT_ID` dari versi lama tidak dipakai lagi
dan boleh dihapus: `gh secret delete SPOTIFY_CLIENT_ID`.

### Setiap rilis

```bash
git tag v0.6.0 && git push origin v0.6.0
```

CI membangun APK dengan keystore yang **sama** dengan build lokal, jadi bisa
dipasang menimpa versi sebelumnya.

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

## Architecture

### Sumber playback

`data/MediaSessionSource.kt` (adapter Android) dan `domain/MediaPlayback.kt`
(keputusan murni, diuji di JVM).

- **Izin.** Android hanya mengizinkan `MediaSessionManager.getActiveSessions`
  untuk app yang punya NotificationListenerService aktif. `SessionAccessService`
  adalah service kosong untuk itu; notifikasi app lain tidak dibaca sama
  sekali. Status izin dibaca dari `enabled_notification_listeners` per
  ComponentName persis, dan dibaca ulang di `MainActivity.onResume`.
- **Memilih sesi** (`pickSession`): yang sedang memutar di perangkat ini, lalu
  yang memutar di mana pun, lalu sesi yang sedang diikuti, lalu yang paling
  baru aktif. Notifikasi diam dari app lain tidak merebut layar.
- **Identitas lagu** (`trackIdFor`): lagu Spotify tetap `spotify:track:<id>`
  (dari mediaId sesi), jadi cache lirik, override, dan offset per lagu dari era
  App Remote tetap terpakai. Player lain memakai `media:<hash>` dari judul dan
  artis yang dinormalisasi, sama untuk lagu yang sama di app berbeda.
- **Posisi** (`positionNowMs`): posisi terakhir + (sekarang − waktu laporan) ×
  kecepatan, beku saat tidak memutar, dijepit ke durasi. Buffering dan pindah
  lagu mempertahankan status pause sebelumnya supaya lirik tidak berkedip.
- **Koreksi jam** (`domain/PlaybackClock.kt`): laporan player tidak dipakai
  mentah. Jitter < 15 ms diabaikan, lompatan >= 1 s dianggap seek. Laporan pause
  yang 0-150 ms di belakang jam (fade) diabaikan; 150 ms-5 s di belakang (pause
  basi YouTube Music, sekitar 1,3 s) diabaikan DAN selisihnya dipakai sebagai
  bias kalau player melanjutkan dari angka basinya. Buffering membekukan jam,
  kecuali di 2 s terakhir lagu (Spotify menyiapkan lagu berikutnya). Satu jam
  per player, dibuat ulang saat pindah player.
- **Konfirmasi seek** (`domain/SeekVerifier.kt`): seek dianggap jalan kalau
  dalam 1,5 s player melapor posisi di sekitar target (±750 ms, ditambah waktu
  yang lewat). Kalau tidak, misalnya Spotify Free yang mengabaikannya diam-diam,
  muncul Toast "<player> didn't accept the seek", sekali per lagu.
- **Kontrol**: `transportControls` milik player. Tombol dan slider mengikuti
  bit `PlaybackState.actions`; tombol yang tidak diizinkan tampil redup, dan
  seek yang tidak diizinkan ditolak sebelum dikirim.
- **Durasi belakangan.** YouTube Music mengirim judul dulu dan durasi
  beberapa ratus milidetik kemudian. Fetch pertama menunggu durasi sampai 2 s;
  kalau durasinya datang lebih telat, lirik dicari ulang melewati cache
  (`shouldRematchForDuration`).
- **AMLL** mencocokkan lewat ID Spotify untuk lagu Spotify, dan lewat judul +
  artis untuk player lain.
- **Debug report** (Settings → Player → COPY DEBUG REPORT / SHARE): versi app
  dan Android, player yang diikuti (paket + versi), lagu dan id-nya, kontrol,
  offset tiga lapis, jawaban tiap sumber lirik, dan 50 kejadian sync terakhir
  (`domain/SyncTrace.kt`, hanya di memori). Disusun murni oleh
  `buildDebugReport`; query string URL dan nilai key/token disensor sebelum
  keluar dari app.
- **Antrean** dari `MediaController.queue` (kalau player membaginya) untuk
  preload lirik 3 lagu berikutnya.

### Tanpa login

Sampai 0.8.x Spotivibe login lewat OAuth PKCE dan menyambung ke Spotify App
Remote. Semua itu sudah dihapus: tidak ada token, client ID, Web API, maupun
supervisor reconnect. Akibat yang perlu diketahui:

- **Kontrol tidak lagi butuh Premium.** Tombol mengikuti `PlaybackState.actions`
  milik player. Spotify Free mengiklankan seek dan skip dengan batasnya sendiri;
  yang ditolak Spotify, ditolak di Spotify.
- **Tidak ada koneksi yang bisa putus.** MediaSession dibaca langsung dari
  sistem, jadi mode "Reconnecting to Spotify…" dan backoff reconnect hilang.
- **Sisa kredensial dihapus.** `PreferencesRepository.purgeLegacySpotifyCredentials`
  membuang token, PKCE, dan status Premium lama di setiap start (menulis hanya
  kalau ada). Daftar key-nya di `domain/LegacyCredentials.kt`, diuji.
- **Data lama tetap terpakai.** Lagu Spotify masih ber-ID `spotify:track:<id>`,
  jadi cache lirik, override, dan offset per lagu tidak hilang saat upgrade.
- **Notification service** berhenti hanya kalau izin notification access
  dicabut, bukan saat player berhenti.

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

### Sumber lirik

Semua sumber ditanya bersamaan (`LyricsRepository.probeNetwork`). Aturannya ada
di `domain/ProviderRace.kt` dan diuji tanpa jaringan:

| Sumber | Peringkat | Dicocokkan lewat | Format | Kode |
|---|---|---|---|---|
| AMLL (`api.amll.dev`) | 10 | ID Spotify di `spotifyIds` (pasti) | TTML | `AmllProvider` |
| lrc.red (`lrc.red/api/v1`) | 20 | judul + artis + durasi ±5 s | TTML | `LrcRedProvider` |
| LRCMux (`api.lrcmux.dev`) | 30 | dicocokkan server (artis, judul, durasi) | JSON per kata | `LrcMuxProvider` |
| LRCLIB (`lrclib.net`) | 100 | `/get` + `/search`, durasi | LRC | `LrclibProvider` |

Pemenang: dalam toleransi durasi dulu, lalu timing paling halus, lalu
peringkat. Perlombaan berhenti begitu pemenang sementara per kata dan tidak ada
sumber berperingkat lebih tinggi yang masih jalan, atau 3 detik setelah konten
pertama datang; sisanya dibatalkan, termasuk request HTTP-nya.

Keputusan yang disengaja:

- **AMLL hanya lewat ID Spotify.** Pencariannya fuzzy: untuk "Blinding Lights"
  item pertamanya "Wasted Times". Lebih baik tidak dapat lirik dari sini
  daripada lirik lagu lain yang timing-nya meyakinkan.
- **lrc.red menolak remix, live, cover.** Hasil pencariannya memuat versi
  remix dan cover oleh artis lain dengan durasi mirip (`domain/TrackMatch.kt`).
  URL TTML-nya hanya diambil kalau https dan host-nya `lrc.red`.
- **BiniLyrics tidak dipanggil.** API-nya me-redirect 307 ke `lrc.red/api/v1`,
  jadi lrc.red dipanggil langsung.
- **Cache negatif tetap milik LRCLIB.** Tanpa konten, "tidak ada" dari LRCLIB
  menang walau sumber komunitas gagal dijangkau. Kalau tidak, lagu tanpa lirik
  ditanyakan ulang setiap diputar.
- **Jeda per sumber** (`ProviderCooldown`): mulai gagal kedua berturut-turut,
  sumber komunitas diistirahatkan 30/60/120/300 s, atau sesuai `Retry-After`.
- **Report per lagu** (`domain/SourceReport.kt`): setiap perlombaan mencatat
  jawaban tiap sumber (berisi + tier, tidak ada, gagal + alasan, dijeda, belum
  ditanya karena perlombaan selesai). Disimpan per lagu, maksimal 8, supaya
  preload antrean tidak menimpa lagu aktif. Pesan "tidak ada lirik" dan "gagal
  memuat" menyebut sumber yang gagal dan golongan alasannya (timed out, rate
  limited, server error, no connection).
- **Pemilih sumber** (menu ⋮ → Search lyrics → "Sources for this song"): satu
  baris per sumber aktif dengan jawabannya untuk lagu ini. Ketuk yang berisi
  untuk memakainya, ketuk yang belum ditanya atau gagal untuk menanyakannya
  sekarang (`LyricsRepository.askSource`). Pilihannya disimpan sebagai override
  (ikut checkbox "Remember"); penanda IN USE dicocokkan dengan lirik yang
  benar-benar tampil, bukan dengan pemenang perlombaan. Override tersimpan kini
  memakai bentuk baris yang sama dengan cache disk, jadi lirik TTML tidak
  kehilangan duet dan vokal latar setelah restart.
- Settings → **Community lyrics** mematikan ketiganya; app kembali ke LRCLIB
  saja. Statistik **COMMUNITY WON** menunjukkan seberapa sering mereka menang.
- `LYRICS_SELECTION_VERSION` naik ke 2, jadi cache dari era LRCLIB-saja dipilih
  ulang saat online dan tetap jadi cadangan offline.
- Spicy Lyrics API sengaja tidak dipakai: butuh kunci `sl_pk_`.

Lane periodik ke API sungguhan (tidak jalan di gate test):

```bash
SPOTIVIBE_LIVE=1 ./gradlew testFullDebugUnitTest --tests "*LiveProvidersTest"
```

### Parsing TTML: suku kata, vokal latar, duet

`domain/TtmlParser.kt` membaca TTML, format lirik Apple Music yang juga dipakai
AMLL dan lrc.red. Hasilnya `SyncedLine` yang sama dengan LRC, ditambah field
yang hanya bisa datang dari TTML:

| Markup TTML | Jadi | Tampil |
|---|---|---|
| `<span begin end>` berdempetan | `SyncedWord` per suku kata, spasi = batas kata | karaoke per suku kata |
| `ttm:role="x-bg"` | `SyncedLine.background` | teks kecil miring di bawah baris |
| `ttm:agent` selain penyanyi utama | `oppositeAligned = true` | rata kanan |
| `ttm:role="x-translation"` / `x-roman` | dibuang | (romanisasi app sendiri) |
| `end` baris → `begin` berikutnya >= 3 s | baris kosong disisipkan | titik-titik jeda |

Penyanyi utama adalah agent yang pertama dipakai sebuah baris, dan agent
bertipe `group` (paduan suara) tidak pernah dianggap pihak kedua duet. Baris
yang isinya cuma vokal latar menaikkan latarnya jadi baris utama, supaya tidak
salah terbaca sebagai penanda jeda.

Parser memakai `javax.xml.parsers` (DOM), bukan `XmlPullParser`: yang kedua
cuma stub di unit test JVM. Dokumen ber-DOCTYPE ditolak sebelum diparse, yang
menutup XXE dan entity expansion di implementasi parser mana pun.

### Animasi karaoke

`domain/WordMotion.kt` (murni, diuji) dan `ui/component/KaraokeLine.kt`.

- **Potongan.** `karaokeGlyphs` mengubah kata bertimer jadi potongan: suku kata
  tanpa spasi menyambung satu kata (tidak dipisah ke baris lain di `FlowRow`),
  dan suku kata >= 1000 ms dipecah per code point dengan jatah waktu rata.
- **Akhir kata.** `endMs` sendiri (TTML, LRCMux), atau awal kata berikutnya
  (enhanced LRC cuma punya waktu mulai), atau akhir baris, atau 500 ms.
- **Kurva.** Natural cubic spline lewat titik kunci: skala 0.95 → 1.0505 (di
  70%) → 1, cahaya 0 → 1 (15%..60%) → 0, angkat 0.01em → −1/60em → 0.
- **Pegas.** Ditulis gaya spr.lua (frekuensi, redaman) lalu dikonversi
  `stiffness = (2πf)²` untuk `spring()` Compose: skala 0.88/0.64, angkat
  1.45/0.4, cahaya 1.18/0.56.
- **Sapuan.** `Brush.horizontalGradient` dengan tepi selebar 20%, posisinya
  `−0.2 + 1.2·p`.
- **Jam per frame.** Posisi dari layar datang tiap 200 ms; di antaranya
  diekstrapolasi dari `withFrameMillis` selama lagu jalan, maksimal 400 ms di
  depan supaya seek atau buffering tidak membuat sapuan kabur ke depan.

Hanya baris aktif yang memakai jalur ini; baris lain tetap `Text` biasa. Efek
mati (`lyric_effects = false`) kembali ke highlight per kata lama.

Lyric effects juga mengendalikan blur menurut jarak (`lineBlurDp` di
`LyricsMetrics.kt`) dan fade tepi daftar (`Modifier.edgeFade`, lapisan
offscreen + `BlendMode.DstIn`). Blur sengaja dilepas selama daftar di-drag
(`collectIsDraggedAsState`): saat itu user sedang membaca baris lain.

### Cache lirik

Tiga lapis: memory LRU 50, file JSON di `filesDir/lyrics-cache`, lalu jaringan.
Dulu di `cacheDir`; isinya dipindahkan sekali saat akses pertama setelah update.
`cacheDir` boleh dikosongkan Android kapan saja, dan itu persis saat yang salah
untuk kehilangan lirik offline.

Lapis disk dibatasi **500 entry atau 8 MB**, mana yang lebih dulu tercapai, plus
buang otomatis apa pun yang tidak dipakai lebih dari 180 hari
(`STALE_RETENTION_MS`). TTL dan retensi sengaja beda: TTL menentukan kapan lirik
ditanyakan ulang ke LRCLIB, retensi menentukan kapan berkasnya dibuang. Entri
yang lewat TTL tidak dihapus saat dibaca, karena ia jadi cadangan saat jaringan
tidak bisa menjawab. Sebelumnya tidak ada batas
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

**Memilih lirik: synced dulu, durasi paling mirip.** Semua kandidat (`/get` dan
setiap item `/search`) diberi peringkat oleh `pickBestCandidate`
(`domain/LyricsSelection.kt`):

| Urutan | Kunci |
|---|---|
| 1 | Synced dengan selisih durasi ≤ 5 s, lalu plain ≤ 5 s, lalu synced di luar toleransi, lalu plain di luar toleransi |
| 2 | Selisih durasi terhadap lagu yang sedang diputar, terkecil dulu |
| 3 | `/get` sebelum `/search` (ikut dicocokkan dengan album) |

Dulu konten `/get` selalu menang walau cuma plain, dan dari `/search` diambil
item synced pertama tanpa melihat durasi, sehingga versi live atau extended bisa
terpilih. Toleransi 5 s ada karena synced dari versi berdurasi lain punya
timestamp yang meleset di sepanjang lagu; `/get` LRCLIB sendiri hanya
menoleransi ±2 s. Jalur cepat (tidak menunggu `/search`) kini hanya untuk `/get`
yang synced dengan selisih durasi ≤ 1 s.

Setiap entri cache mencatat `selectionVersion`. Entri dari aturan lama dianggap
basi: saat online lagunya dipilih ulang, saat offline tetap tersaji sebagai
cadangan. Naikkan `LYRICS_SELECTION_VERSION` setiap kali aturan peringkat
berubah.

Kegagalan sementara diulang sampai 3 kali dengan backoff 400 ms, 800 ms, 1.6 s.
Preload antrean selalu satu percobaan saja, supaya tidak berebut jaringan
dengan lagu yang sedang diputar.

UI membedakan tiga keadaan kosong: "No lyrics found for this track" (LRCLIB
memastikan tidak ada), "Couldn't load lyrics. Check your connection, then replay
the song." (online tapi gagal), dan "You're offline. Lyrics for this song aren't
saved yet." (offline, belum ada cadangan; diambil sendiri saat online lagi).

### Offline

Aturannya satu: panggilan jaringan yang gagal memakai nilai terakhir yang
diketahui, dan state tidak pernah turun ke "gagal" hanya karena offline.
Keputusan lengkap dan alternatif yang ditolak ada di
[`docs/superpowers/specs/2026-10-05-offline-mode-design.md`](docs/superpowers/specs/2026-10-05-offline-mode-design.md).

| Data | Sumber | Saat offline |
|---|---|---|
| Info lagu, posisi, kontrol, cover | MediaSession player (lokal) | Jalan normal |
| Romanisasi | Lokal | Jalan normal |
| Lirik | Disk cache, lalu LRCLIB | Cache segar atau basi, tanpa panggilan jaringan |
| Preload antrean | `MediaController.queue` + sumber lirik | Dilewati |

`NetworkMonitor` mengikuti default network lewat
`registerDefaultNetworkCallback`. Online berarti punya `NET_CAPABILITY_INTERNET`
dan tidak terdeteksi captive portal. `NET_CAPABILITY_VALIDATED` sengaja tidak
disyaratkan: endpoint validasi Google diblokir di sebagian jaringan, dan salah
mengira offline membuat app berhenti mengambil lirik sama sekali.

Saat jaringan kembali, `PlaybackController` mengambil ulang lirik lagu aktif
kalau tadi `Offline` atau `Unavailable`.

Yang tetap butuh internet: lirik lagu yang belum pernah diputar atau masuk
antrean saat online. Musik offline sendiri tetap urusan player-nya.

Trace di logcat: `adb logcat -s NetworkMonitor LyricsRepository PlaybackController`
menampilkan `online=<bool>`, `stale disk hit`, dan `offline tanpa cadangan`.

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

#### Offset tiga lapis

Offset yang dipakai engine adalah jumlah tiga lapis (`domain/SyncOffsets.kt`):

| Lapis | Disetel di | Kunci simpan | Batas |
|---|---|---|---|
| Global | Settings → Sync offset | `lyrics_offset_ms` | ±2000 ms |
| Perangkat output | Settings → This output | `device_offset_<hash>` | ±2000 ms |
| Lagu | Menu ⋮ → Sync this song | `song_offset_<track id>` | ±2000 ms |

Jumlahnya dijepit ±5000 ms. Lewat dari itu yang salah bukan sync-nya tapi
liriknya (versi lain lagunya), dan obatnya pencarian lirik.

Kenapa dipecah: jeda Bluetooth mobil bisa 150 sampai 300 ms sedangkan speaker
HP hampir nol, jadi offset global yang disetel untuk mobil membuat lirik
terlalu cepat begitu kembali ke speaker. Satu file LRC yang telat juga tidak
boleh memaksa lagu lain ikut digeser.

Perangkat aktif dibaca `AudioOutputMonitor` lewat `AudioDeviceCallback`.
Android tidak memberi tahu perangkat mana yang dipakai player, jadi dipakai
urutan routing media bawaan sistem: Bluetooth (A2DP/BLE) > headset kabel/USB >
output eksternal (bus audio head unit, HDMI, line) > speaker internal. SCO
(jalur panggilan) diabaikan. Kuncinya SHA-256 dari tipe + nama produk, bukan
alamat MAC, supaya tidak butuh izin `BLUETOOTH_CONNECT` dan nama perangkat
tidak tersimpan mentah. Nilai 0 menghapus key-nya, jadi jumlah key hanya
sebanyak yang pernah disetel user.

### Layout: responsif dan safe area

Tiga lapis, masing-masing punya satu pemilik di `ui/theme/`:

| Lapis | File | Tugas |
|---|---|---|
| Kelas ukuran | `SvWindow.kt` | Keputusan struktur: satu kolom atau dua panel, album besar atau kecil. Ambang mengikuti breakpoint resmi Android |
| Skala terjepit | `SvScale.kt` | `.svDp` / `.svSp`, padanan `.w` / `.sp` flutter_screenutil, tapi faktornya dijepit 0,85 sampai 1,25 supaya elemen tidak membengkak saat landscape |
| Safe area | `SvInsets.kt` | `svSafeContent()` mendorong konten keluar dari bawah status bar, navigation bar (di sisi mana pun), cutout, dan keyboard |

Safe area dibuat karena `enableEdgeToEdge()` (dan `targetSdk` 35+ yang memaksanya)
membuat app menggambar di belakang bar sistem, sementara dulu tidak ada layar
yang memakai insets. Jarak atas cuma angka tetap 16/32 dp. Itu cukup untuk
status bar HP, tapi di head unit mobil bar sistemnya lebih tebal atau berada di
samping, sehingga header tertimpa navigasi.

Aturannya:

- **Latar dulu, insets kemudian:** `.background(...).svSafeContent()`. Urutan
  modifier itu yang membuat `AmbientBg`, scrim, dan tint panel tetap sampai tepi
  layar sementara kontennya masuk ke area aman.
- **Panel landscape memakai sisi parsial.** Panel kiri `Start + Vertical`, panel
  kanan `End + Vertical`, supaya navigasi kiri head unit tidak ikut memakan ruang
  panel kanan.
- **Insets di container, bukan di `contentPadding` list.** `LyricsList` mengukur
  posisi chrome lewat `positionInRoot`, jadi padding di parent sudah masuk
  hitungan anchor baris aktif.
- **Jangan baca `WindowInsets.safeDrawing` langsung.** Semua lewat
  `svSafeInsets()`, supaya test bisa menyuntikkan insets palsu lewat
  `LocalSvSafeInsets`.

`ResponsiveGuardTest` menjaga aturan ini di gate lane: setiap layar di
`ui/screen/` (kecuali router `MainScreen`) wajib memanggil `svSafeContent`, insets
sistem hanya boleh dibaca di `SvInsets.kt`, dan pola lama
`padding(top = if (isLandscape) ...)` tidak boleh kembali. `SafeAreaUiTest`
(instrumentasi) memasang insets ala head unit (atas 64 dp, kiri 80 dp, bawah 48
dp) lalu memastikan judul lagu dan tombol transport berada di dalam area aman.

Floating overlay hidup di window terpisah, jadi insets-nya dibaca dari root
insets view overlay sendiri (`OverlayManager.safeInsets`, jalan juga di Android
9/10 yang umum di head unit). Posisi Y dijepit di antara bar atas dan bawah
(`clampOverlayY`), dan lebarnya mengikuti area aman (`overlayHorizontalBounds`),
bukan `MATCH_PARENT`. Perangkat yang tidak melaporkan insets jatuh ke perilaku
lama.

Cek di head unit: buka app, header tidak tertimpa. Putar lagu, transport tidak
tertimpa. Di landscape, tombol previous tidak berada di bawah navigasi kiri.
Nyalakan overlay, lalu pastikan ia tidak bisa diseret ke bawah bar sistem. Kalau
masih tertimpa, kemungkinan bar navigasinya digambar oleh launcher pihak ketiga,
bukan system bar sungguhan. Android tidak melaporkannya sebagai insets, dan tidak
ada API yang bisa melihatnya.

### Container

```
SpotivibeApp (Application)
├── applicationScope (SupervisorJob + Main.immediate)
├── preferencesRepository (DataStore; hapus kredensial Spotify lama saat start)
├── networkMonitor ─ default network callback → isOnline
├── audioOutputMonitor ─ perangkat output aktif → offset per perangkat
├── playbackSource ─ MediaSession player apa pun (butuh notification access)
├── lyricsRepository ─ 4 sumber berlomba + 3-layer cache (mem LRU 50, disk JSON, network), stale-if-offline
├── romanizationService ─ JA/KR/ZH lazy-init
└── playbackController ─ orchestrator
        ├── observe nowPlaying (MediaSession player yang diikuti)
        ├── fetch lyrics on track change, ulang saat online kembali / durasi datang
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
- `ACCESS_NETWORK_STATE` — `NetworkMonitor` untuk mode offline
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — notification service hidup di background
- `POST_NOTIFICATIONS` — Android 13+ runtime
- `SYSTEM_ALERT_WINDOW` — floating overlay (granted via Settings)
- `VIBRATE` — haptic tick saat baris lirik berganti
- Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE` pada `SessionAccessService`) —
  satu-satunya cara membaca MediaSession app lain. Notifikasinya sendiri tidak dibaca

## Tested on

- POCO X7 Pro / Xiaomi 2412DPC0AG, HyperOS 3.0.3 (Android 14+)
- Pixel 9 Pro (emulator)

## Compatibility caveats

- **Kontrol mengikuti player.** Tombol yang tidak diiklankan player lewat
  `PlaybackState.actions` tampil redup; Spotify Free tetap menerapkan batas
  skip-nya sendiri
- **Head unit tanpa layar notification access**: beri izin lewat adb (lihat Setup)
- **Min SDK 26** (Android 8) — covers ~95% device global
- **HyperOS Dynamic Island** tidak di-hijack karena pakai `foregroundServiceType=specialUse` (bukan `mediaPlayback`)
- **MIUI Autostart + Battery No Restrictions** untuk Spotivibe dan player-nya wajib dinyalakan biar service stabil

## License

MIT (rencananya) — TODO file `LICENSE`.

## Credits

- LRCLIB.net — community lyrics database
- kuromoji © Atilika Inc.
- pinyin4j © Li Min
- Korean Revised Romanization port internal
