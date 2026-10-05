# Mode offline

Tanggal: 2026-10-05
Status: diimplementasikan

## Masalah

Sebagian besar Spotivibe sudah jalan tanpa internet. Info lagu, posisi,
kontrol, dan cover datang dari App Remote, yaitu IPC ke app Spotify di HP yang
sama, bukan dari Web API. Romanisasi juga lokal. Yang rusak saat offline ada
empat:

1. `LyricsCache.get` **menghapus** entri yang lewat TTL 30 hari lalu return
   null. Offline ditambah entri basi berarti lirik hilang, padahal datanya masih
   ada di disk sampai detik itu.
2. Cache ada di `cacheDir`, yang boleh dikosongkan Android kapan saja saat
   storage sesak.
3. Tidak ada deteksi jaringan. Offline tetap memanggil LRCLIB (3 percobaan
   dengan backoff), `/me`, dan `/queue`, masing-masing menunggu timeout koneksi
   5 detik. UI cuma bilang "Couldn't load lyrics", dan tidak ada fetch ulang
   saat jaringan kembali.
4. Status Premium (`/me`) hanya ada di memori. Cold start offline selalu
   mulai dari `Unknown`.

## Sumber data per fitur

| Data | Sumber | Butuh internet? |
|---|---|---|
| Judul, artis, album, durasi, posisi, play/pause | App Remote `PlayerState` | Tidak |
| Cover album | App Remote `imagesApi` | Tidak, selama Spotify menyimpan cover-nya |
| Kontrol transport | App Remote `playerApi` | Tidak |
| Romanisasi | kuromoji, pinyin4j, aturan Korea internal | Tidak |
| Status Premium | Web API `/me` | Ya, nilai terakhir disimpan |
| Antrean untuk preload | Web API `/me/player/queue` | Ya, dilewati saat offline |
| Lirik baru | LRCLIB | Ya, cadangan dari disk |
| Refresh token | `accounts.spotify.com` | Ya. Gagal sementara tidak membuang kredensial |

## Keputusan

Satu aturan: **panggilan jaringan yang gagal memakai nilai terakhir yang
diketahui. State tidak pernah turun ke "gagal" atau "tidak tahu" hanya karena
offline.**

### TTL dan retensi dipisah

TTL (30 hari berisi, 1 hari kosong) menentukan kapan lirik **ditanyakan ulang**
ke LRCLIB. Retensi (`STALE_RETENTION_MS`, 180 hari sejak terakhir dipakai)
menentukan kapan berkasnya **dibuang**. Dulu keduanya sama-sama 30 hari, jadi
berkas dibuang tepat saat ia berubah jadi basi, dan tidak pernah ada cadangan.
Batas 500 entri / 8 MB tetap menjadi pengaman ukuran yang utama.

### Stale-if-error, hanya untuk entri berisi

`resolveWithStale` mengganti `Offline` dan `Unavailable` dengan lirik basi
kalau ada. `NotFound` dari LRCLIB **tidak** diganti, karena itu jawaban pasti
yang baru saja ditanyakan. Kalau entri LRCLIB dihapus karena salah lagu, lirik
basi yang salah itu yang akan terus tampil. Entri negatif basi juga tidak
dipakai: "dulu tidak ada" tidak menjelaskan apa pun tentang sekarang.

Lirik basi tidak dimasukkan ke mem cache. Mem cache dicek lebih dulu dari
jaringan, jadi memasukkannya berarti lirik basi menang terus sepanjang sesi
walaupun jaringan sudah kembali.

### Offline tidak menyentuh jaringan sama sekali

Kalau `NetworkMonitor` bilang offline, `LyricsRepository` langsung ke cadangan
disk. Retry juga berhenti begitu jaringan hilang di tengah percobaan, dan hasil
akhirnya `Offline`, bukan `Unavailable`, supaya UI jujur dan fetch ulang
terpicu saat online.

### `NET_CAPABILITY_VALIDATED` sengaja tidak disyaratkan

Validasi Android bergantung pada endpoint cek milik Google, yang diblokir di
sebagian jaringan kantor dan beberapa negara. Kalau disyaratkan, app akan
menganggap dirinya offline selamanya di jaringan yang sebenarnya bisa
menjangkau LRCLIB. Salah mengira online jauh lebih murah: probe gagal, lalu
lirik basi tetap dipakai. Yang dipakai hanya bukti positif: tidak ada
kapabilitas internet, atau captive portal yang terdeteksi.

### Status Premium disimpan, diverifikasi ulang sekali per sesi

`last_known_product` di DataStore jadi nilai awal `_product`. Flag
`productVerified` di memori memastikan `/me` tetap dicek sekali per sesi saat
online, karena user bisa saja upgrade atau downgrade sejak terakhir dicek.
Nilainya dihapus di `clearTokens`, yang dipanggil oleh logout **dan** oleh
refresh yang ditolak permanen, karena nilai ini milik akun, dan login berikutnya
bisa saja akun lain. Penolakan App Remote tetap menang atas nilai apa pun.

### Statistik

Lirik basi dicatat sebagai `DiskCache`, sumber yang benar-benar menyajikannya.
Offline tanpa cadangan **tidak dicatat**. Statistik mengukur coverage LRCLIB,
dan lookup yang tidak pernah bertanya ke LRCLIB tidak bilang apa pun soal
coverage.

## Alternatif yang ditolak

- **Room / SQLite untuk cache lirik.** JSON per berkas sudah cukup untuk 500
  entri. Pindah ke Room menambah migrasi skema tanpa memperbaiki satu pun dari
  empat masalah di atas.
- **WorkManager untuk sync lirik di latar.** Preload antrean 3 lagu sudah ada.
  Job latar berkala menambah beban baterai untuk lagu yang belum tentu diputar.
- **Download lirik per playlist.** Butuh scope OAuth `playlist-read-private`,
  artinya semua user harus login ulang, plus satu layar baru. Ditunda sampai
  ada permintaan nyata.
- **Menyimpan audio.** Tidak disediakan SDK dan melanggar ToS Spotify. Musik
  offline tetap lewat fitur download di app Spotify.

## Pengujian

Gate lane (JVM, tanpa perangkat):

- `NetworkStatusTest`: `isUsableNetwork` dan `shouldRefetchOnReconnect`.
- `LyricsLookupTest`: tabel `resolveWithStale`.
- `LyricsOfflineTest`: repository dengan `FakeLrclib` yang menghitung panggilan.
  Bukti utamanya adalah nol panggilan saat offline. Ada juga skenario perjalanan
  end-to-end di atas `LyricsCache` sungguhan dengan jam palsu: online, offline
  31 hari kemudian, lagu baru, lalu online lagi.
- `LyricsCacheTest`: cache sungguhan di folder sementara. Entri basi tetap ada
  di disk, `getStale`, dan migrasi `cacheDir` ke `filesDir`.
- `PlaybackDecisionsTest`, `CacheKeysTest`, `CacheEvictionTest`,
  `LyricsStatsTest`, `HeaderEyebrowTest`, `ManifestPermissionTest`.

Uji mutasi dilakukan sekali: mengembalikan `delete()` untuk entri kedaluwarsa
di `LyricsCache.get` membuat `LyricsCacheTest` dan skenario end-to-end gagal.

Lane instrumentasi: `LyricsListUiTest.offlineTidakMenyuruhReplay`.

Tidak ada eval berbayar. Fitur ini tidak punya komponen LLM, jadi skenario
regresi deterministik di gate lane adalah pengukur yang jujur.

## Uji manual di perangkat

1. Online, putar lagu A sampai liriknya muncul.
2. Nyalakan mode pesawat, matikan paksa Spotivibe, buka lagi.
3. Harus terlihat: eyebrow header "OFFLINE · <album>", lirik A tampil, tombol
   kontrol ada.
4. Putar lagu B yang belum pernah diputar. Pesan yang benar: "You're offline.
   Lyrics for this song aren't saved yet."
5. Matikan mode pesawat. Lirik B muncul sendiri tanpa replay.
6. `adb logcat -s NetworkMonitor LyricsRepository PlaybackController` harus
   menampilkan `online=false`, `stale disk hit` atau `offline tanpa cadangan`,
   lalu `online=true` dan `Online lagi, ambil ulang lirik lagu aktif`.

Belum terverifikasi: apakah App Remote bisa connect ulang dengan
`showAuthView=false` saat cold start dalam mode pesawat. Ini perilaku SDK
Spotify, dan langkah 2 di atas yang akan menjawabnya.

## Yang sengaja tidak dibuat

- Tombol "coba lagi" untuk `Unavailable` saat online (LRCLIB 5xx). Itu tidak
  terkait offline, dan replay lagu tetap menjadi jalan keluarnya.
- Menyegarkan lirik basi yang sudah tampil saat jaringan kembali. Liriknya
  sudah ada, dan lirik lagu jarang berubah. Lirik segar akan diambil di
  pemutaran berikutnya.
