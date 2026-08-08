# Logo Spotivibe

## Idenya

Satu hal yang paling membedakan app ini: **satu baris lirik menyala mengikuti
musik.** Bukan memutar musik, itu tugas Spotify. Bukan karaoke pada umumnya.
Satu baris, tepat pada waktunya.

Jadi mark-nya adalah lima baris lirik dengan lebar yang tidak rata, dan yang
tengah menyala.

Warna, alpha, dan hierarki ukurannya bukan karangan. Semuanya diambil persis
dari `LyricLineState` di
[`LyricsList.kt`](../app/src/main/java/com/tglabs/spotivibe/ui/component/LyricsList.kt):

| Baris | Token | Warna | Alpha | Tinggi batang |
|---|---|---|---|---|
| Active | `accent` | `#EC6A5C` | 1.00 | 8 |
| Near (±1) | `ink2` | `#C2BFB6` | 0.85 | 6 |
| Far (±2) | `ink3` | `#8A877F` | 0.55 | 5 |

Tinggi batangnya mengikuti skala tipografi yang sama dengan yang dilihat user
(30 / 22 / 18 sp), jadi hierarki di logo dan di layar identik. Lebar yang tidak
rata meniru baris teks sungguhan, dan itu yang membedakannya dari ikon "daftar"
yang batangnya seragam.

Latar `#14151B` (`BgDark0`).

## File

| File | Ukuran | Untuk |
|---|---|---|
| `spotivibe-icon.svg` | 108 | Ikon penuh dengan latar. Sumber untuk launcher. |
| `spotivibe-icon-foreground.svg` | 108 | Lapisan foreground adaptive icon, latar transparan. Dipakai juga untuk themed icon. |
| `spotivibe-mark.svg` | 58×53 | Mark telanjang, viewBox rapat. Untuk README dan header. |
| `spotivibe-notification.svg` | 24 | Ikon status bar, monokrom. |
| `spotivibe-wordmark.svg` | 300×64 | Lockup horizontal mark + wordmark. |

## Zona aman

Semua isi ikon berada di dalam lingkaran berjari-jari 33 berpusat di (54, 54)
pada kanvas 108. Itu zona aman adaptive icon Android, jadi mask bulat maupun
squircle tidak memotong apa pun. Sudah diverifikasi per sudut batang, bukan
dikira-kira.

## Kenapa baris aktif dibedakan lewat ukuran, bukan cuma warna

Themed icon Android 13+ membuang warna dan mewarnai ulang seluruh bentuk dengan
satu warna, walaupun alpha tetap dihormati. Ikon status bar bahkan cuma memakai
kanal alpha. Karena itu baris aktif dibuat **paling tinggi dan paling lebar**,
supaya tetap terbaca sebagai "baris yang sedang dinyanyikan" saat warnanya
diseragamkan.

Ikon notification turun dari lima baris jadi tiga, karena lima tidak terbaca di
sekitar 18 px. Proporsinya dipertahankan: yang tengah tetap paling besar.

## Yang belum selesai

**Wordmark masih memakai `<text>`, bukan path.** Font Instrument Serif ada di
repo (`app/src/main/res/font/instrument_serif_regular.ttf`) tapi tidak
ter-embed di SVG, jadi di mesin yang belum memasangnya akan jatuh ke Georgia
dan bentuknya berbeda. Untuk dipakai di README GitHub atau store listing,
teksnya harus dikonversi jadi outline dulu.

**Belum dipasang ke app.** `ic_launcher_foreground.xml` masih bugdroid bawaan
Android Studio, dan `ic_notification.xml` masih not balok generik. Memasangnya
berarti mengonversi SVG di sini jadi Android vector drawable.
