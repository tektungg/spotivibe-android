# Pencarian lirik manual dan override

Tanggal: 2026-08-09
Status: disetujui, siap implementasi

## Masalah

Auto-match ke LRCLIB kadang memuat lirik dari **lagu yang sama sekali berbeda**,
biasanya karena judul atau artis di Spotify tidak cocok dengan yang ada di
database LRCLIB. Sekarang tidak ada jalan keluar sama sekali: lirik salahnya
muncul lagi setiap kali lagu itu diputar.

Bukan kasus "versi live atau durasi beda". Yang dilaporkan adalah lagunya
memang salah, jadi solusinya **pencarian teks bebas**, bukan sekadar memilih
match lain untuk query yang sama.

## Keputusan

### Simpan lirik pilihan secara utuh, bukan hanya id LRCLIB

Alternatif yang ditolak: menyimpan id LRCLIB lalu mengambil ulang tiap kali
perlu. Penyimpanannya memang jauh lebih kecil, tapi setiap cache miss butuh
jaringan, dan saat offline lagu yang di-override justru jadi **tidak berlirik
sama sekali**, lebih buruk daripada jalur otomatis yang setidaknya punya cache.
Untuk fitur yang tujuannya "jangan pernah salah lagi", bergantung pada jaringan
bertentangan dengan tujuannya sendiri.

Id LRCLIB tetap ikut disimpan sebagai catatan, supaya fitur "segarkan override"
bisa ditambahkan nanti tanpa mengubah format penyimpanan.

### Simpan di `filesDir`, BUKAN `cacheDir`

Override adalah keputusan user, bukan cache. `cacheDir` boleh dihapus sistem
kapan saja saat penyimpanan sesak, dan kehilangan keputusan user karena itu
tidak bisa diterima. Ini juga membuatnya kebal dari pembatasan LRU cache lirik.

### Memilih hasil TIDAK otomatis menyimpan

User mungkin mencoba beberapa hasil sebelum ketemu yang benar, dan tiap
percobaan tidak seharusnya jadi komitmen. Ada centang **"Ingat lirik ini untuk
lagu ini"** yang harus dicentang sadar.

Centangnya **tidak lengket**: selalu kembali kosong tiap kali layar pencarian
dibuka. Menyimpan permanen harus selalu tindakan sadar, tidak pernah terjadi
karena lupa mematikannya dari pencarian sebelumnya.

### Satu peta override, bukan dua konsep

Pilihan sesi dan pilihan permanen hidup di **satu** map di memori. Bedanya cuma
apakah ikut ditulis ke disk.

```
overrides: Map<trackId, LyricsResult>
   ├─ startup      : diisi dari store disk
   ├─ tanpa centang: memori saja, hilang saat proses mati
   └─ dengan centang: memori DAN disk
```

Satu lookup, satu konsep. Tanpa ini akan ada dua lapis resolusi yang harus
dijaga tetap konsisten.

## Aliran data

```
fetchLyrics(trackId, ...)
   ├─ 0. overrides       ← BARU, menang atas segalanya
   ├─ 1. mem cache LRU
   ├─ 2. disk cache
   └─ 3. jaringan (/get + /search)
```

Konsekuensi yang disengaja: lagu yang sudah di-override **berhenti** menanyakan
LRCLIB selamanya. Itu memang yang diminta, dan efek sampingnya mengurangi beban
ke API komunitas gratis.

Statistik lirik mendapat sumber baru `Override`, supaya angka coverage tidak
terdistorsi oleh lagu yang sudah diperbaiki manual.

## Antarmuka

Kebab di header berubah dari "langsung buka Settings" jadi menu kecil:

- **Cari lirik**
- **Lupakan lirik tersimpan** — hanya muncul kalau lagu ini punya override
  permanen. Tanpa ini user terjebak dengan pilihan yang ternyata salah.
- **Settings**

Layar pencarian:

```
CARI LIRIK                          ✕
─────────────────────────────────────
[ Judul lagu     ] ← terisi, bisa diedit
[ Artis          ] ← terisi, bisa diedit
                        [ CARI ]
─────────────────────────────────────
☐ Ingat lirik ini untuk lagu ini
─────────────────────────────────────
Bohemian Rhapsody
Queen · A Night at the Opera
5:55 · TER-SYNC
─────────────────────────────────────
Bohemian Rhapsody (Live)
Queen · Live at Wembley
6:12 · TEKS POLOS
```

Judul dan artis terisi otomatis dari Spotify tapi **bisa diedit**, karena justru
ketidakcocokan judul itu yang bikin auto-match meleset.

Tiap hasil menampilkan album, durasi, dan apakah ter-sync. Durasi yang paling
menentukan untuk mengenali versi. Semua data ini sudah ada di `LrclibDto`, jadi
tidak perlu request tambahan.

Tap satu hasil langsung menerapkan dan menutup layar.

## Penanganan error

| Kejadian | Perilaku |
|---|---|
| Cari saat offline | Pesan di dalam layar, tombol coba lagi. Lirik yang sedang tampil tidak diganggu |
| Hasil kosong | "Tidak ada hasil. Coba ubah judul atau artisnya" |
| Hasil instrumental atau tanpa lirik | Disaring dari daftar |
| Gagal menulis ke store | Pilihan **tetap berlaku** di memori, dicatat di log saja |
| File override rusak | Dibuang, jatuh balik ke auto-match |

Prinsipnya sama dengan `LyricsStatsRecorder`: kegagalan menyimpan tidak boleh
menjatuhkan fitur.

## Pengujian

Yang diuji murni, mengikuti pola yang sudah berjalan:

- Urutan resolusi: override menang atas mem, disk, dan jaringan. Store jadi
  antarmuka yang di-inject seperti `LyricsStatsRecorder`, jadi bisa di-fake
- Penyaringan hasil: instrumental dan yang tanpa lirik tidak boleh muncul
- Pemetaan hasil ke baris tampilan: format durasi `5:55`, penanda ter-sync
- Sesi versus permanen: tanpa centang tidak menyentuh disk; dengan centang
  menulis; muat ulang mengembalikan yang permanen saja
- Lupakan: menghapus dari memori dan disk sekaligus

Tidak diuji: tampilan sheet-nya sendiri. Logikanya ditarik keluar supaya yang
tersisa di Composable cuma penggambaran.

## Yang sengaja tidak dibuat

- Daftar semua lirik tersimpan di Settings. Belum ada bukti butuh pengelolaan
  massal; "lupakan" per lagu sudah cukup
- Tombol "segarkan override". Id LRCLIB disimpan supaya bisa ditambah nanti
- Pencarian di dalam lirik yang sedang tampil. Fitur berbeda meski namanya mirip
