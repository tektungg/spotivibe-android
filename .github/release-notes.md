Dua varian APK. Bedanya **hanya** dukungan romanisasi Jepang.

| Berkas | Jepang | Korea | Mandarin |
|---|---|---|---|
| `spotivibe-<versi>.apk` | ya | ya | ya |
| `spotivibe-<versi>-lite.apk` | tidak | ya | ya |

Varian `lite` jauh lebih kecil karena tidak membawa kamus IPADIC milik
kuromoji, yang mengisi 81% ukuran varian penuh. Kanji tidak bisa diromanisasi
tanpa kamus morfologis, jadi tidak ada versi ringan dari kemampuan itu:
pilihannya memuat kamusnya atau tidak mendukung Jepang.

Di varian `lite`, tombol romanisasi tidak muncul untuk lagu berbahasa Jepang.
Itu disengaja; kontrol yang ada tapi tidak berfungsi lebih membingungkan
daripada kontrol yang tidak ada.

Keduanya memakai applicationId yang sama, jadi yang satu menimpa yang lain saat
dipasang. Itu juga disengaja: dua app terpasang berbarengan akan berebut
redirect auth `spotivibe://callback`.
