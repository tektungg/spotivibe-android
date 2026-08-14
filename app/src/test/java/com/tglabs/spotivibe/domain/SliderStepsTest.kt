package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderStepsTest {

    private val garisFont = tickCountFor(FONT_SIZE_MIN, FONT_SIZE_MAX, FONT_SIZE_STEP)
    private val garisOffset =
        tickCountFor(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, LYRICS_OFFSET_STEP_MS)
    private val garisSpasi = tickCountFor(LINE_SPACING_MIN, LINE_SPACING_MAX, LINE_SPACING_STEP)

    // ── Jumlah garis diturunkan, bukan diketik ───────────────────

    /**
     * REGRESI: jumlah garis dulu dipatok 21 untuk ketiga slider, tanpa hubungan
     * apa pun dengan berapa nilai yang sebenarnya bisa dipilih. Sync offset
     * punya 4001 nilai di balik 21 garis, jadi gagangnya berhenti di antara
     * garis dan layar menampilkan `+513 ms`, angka yang tidak diwakili garis
     * mana pun.
     */
    @Test
    fun `jumlah garis cocok dengan jumlah nilai yang bisa dipilih`() {
        assertEquals(23, garisFont)
        assertEquals(21, garisOffset)
        assertEquals(21, garisSpasi)
    }

    /**
     * Rentang HARUS habis dibagi langkahnya. Kalau tidak, garis terakhir tidak
     * akan mewakili nilai maksimum, dan setelan tidak pernah bisa disetel ke
     * ujung atasnya.
     */
    @Test
    fun `rentang habis dibagi langkahnya`() {
        listOf(
            Triple(FONT_SIZE_MIN, FONT_SIZE_MAX, FONT_SIZE_STEP),
            Triple(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, LYRICS_OFFSET_STEP_MS),
            Triple(LINE_SPACING_MIN, LINE_SPACING_MAX, LINE_SPACING_STEP),
        ).forEach { (min, max, step) ->
            assertEquals("rentang $min..$max langkah $step", 0, (max - min) % step)
        }
    }

    // ── Bolak-balik nilai dan indeks ─────────────────────────────

    @Test
    fun `garis pertama dan terakhir mewakili ujung rentangnya`() {
        assertEquals(FONT_SIZE_MIN, tickValueOf(0, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
        assertEquals(
            FONT_SIZE_MAX,
            tickValueOf(garisFont - 1, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont),
        )
        assertEquals(
            LYRICS_OFFSET_MIN_MS,
            tickValueOf(0, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset),
        )
        assertEquals(
            LYRICS_OFFSET_MAX_MS,
            tickValueOf(garisOffset - 1, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset),
        )
    }

    /** Garis tengah slider dua sisi harus tepat nol, bukan mendekati nol. */
    @Test
    fun `garis tengah sync offset tepat nol`() {
        val tengah = (garisOffset - 1) / 2
        assertEquals(
            0,
            tickValueOf(tengah, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset),
        )
    }

    @Test
    fun `setiap garis menghasilkan nilai bulat dan tetap`() {
        (0 until garisOffset).forEach { i ->
            val nilai = tickValueOf(i, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset)
            assertEquals(
                "nilai garis $i harus kelipatan langkahnya",
                0,
                (nilai - LYRICS_OFFSET_MIN_MS) % LYRICS_OFFSET_STEP_MS,
            )
        }
    }

    @Test
    fun `nilai garis kembali ke indeks yang sama`() {
        listOf(
            Triple(FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont),
            Triple(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset),
            Triple(LINE_SPACING_MIN, LINE_SPACING_MAX, garisSpasi),
        ).forEach { (min, max, jumlah) ->
            (0 until jumlah).forEach { i ->
                val nilai = tickValueOf(i, min, max, jumlah)
                assertEquals("$min..$max garis $i nilai $nilai", i, tickIndexOf(nilai, min, max, jumlah))
            }
        }
    }

    @Test
    fun `nilai naik seiring indeks naik`() {
        var sebelumnya = Int.MIN_VALUE
        (0 until garisFont).forEach { i ->
            val nilai = tickValueOf(i, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont)
            assertTrue("mundur di garis $i", nilai > sebelumnya)
            sebelumnya = nilai
        }
    }

    // ── Nilai lama yang tidak jatuh di garis ─────────────────────

    /**
     * Nilai `+513 ms` dari perangkat pengguna tidak jatuh di garis mana pun.
     * Ia harus menempel ke garis TERDEKAT, bukan dibulatkan ke bawah, supaya
     * gagangnya tidak melompat jauh saat setelan lama dibuka.
     */
    @Test
    fun `nilai lama menempel ke garis terdekat`() {
        val i = tickIndexOf(513, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset)
        assertEquals(
            "513 lebih dekat ke 600 daripada ke 400",
            600,
            tickValueOf(i, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset),
        )
    }

    @Test
    fun `pembulatan ke terdekat, bukan ke bawah`() {
        // 17 sp ada di antara garis 16 dan 18; 17 tepat di tengah, dibulatkan naik.
        val i = tickIndexOf(17, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont)
        assertEquals(18, tickValueOf(i, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))

        val j = tickIndexOf(15, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont)
        assertEquals("15 lebih dekat ke 16 daripada ke 14", 16, tickValueOf(j, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
    }

    // ── Posisi sentuh ────────────────────────────────────────────

    @Test
    fun `sentuhan di tepi menghasilkan garis ujung`() {
        assertEquals(0, tickIndexAt(0f, 21))
        assertEquals(20, tickIndexAt(1f, 21))
    }

    @Test
    fun `sentuhan di tengah menghasilkan garis tengah`() {
        assertEquals(10, tickIndexAt(0.5f, 21))
    }

    @Test
    fun `sentuhan di luar lebar dijepit`() {
        assertEquals(0, tickIndexAt(-5f, 21))
        assertEquals(20, tickIndexAt(5f, 21))
    }

    /**
     * Lebar nol menghasilkan pembagian nol. Tanpa penjagaan, satu sentuhan
     * sebelum tata letak selesai membuat app jatuh.
     */
    @Test
    fun `pecahan NaN tidak menjatuhkan apa pun`() {
        assertEquals(0, tickIndexAt(Float.NaN, 21))
    }

    @Test
    fun `hasil selalu di dalam rentang indeks`() {
        listOf(-99f, -1f, 0f, 0.33f, 0.5f, 1f, 99f, Float.NaN).forEach { f ->
            listOf(2, 5, 21, 23, 41).forEach { n ->
                assertTrue("f=$f n=$n", tickIndexAt(f, n) in 0 until n)
            }
        }
    }

    // ── Ketahanan ────────────────────────────────────────────────

    @Test
    fun `satu garis atau rentang kosong tidak melempar exception`() {
        assertEquals(0, tickIndexOf(50, 10, 10, 1))
        assertEquals(10, tickValueOf(0, 10, 10, 1))
        assertEquals(0, tickIndexAt(0.5f, 1))
        assertEquals(1, tickCountFor(10, 10, 1))
        assertEquals(1, tickCountFor(0, 20, 0))
    }

    @Test
    fun `nilai di luar rentang dijepit ke garis ujung`() {
        assertEquals(0, tickIndexOf(-999, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
        assertEquals(garisFont - 1, tickIndexOf(999, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
    }

    @Test
    fun `indeks di luar rentang dijepit`() {
        assertEquals(FONT_SIZE_MIN, tickValueOf(-5, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
        assertEquals(FONT_SIZE_MAX, tickValueOf(999, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
    }

    @Test
    fun `nilai hasil slider selalu lolos penjepit preferensi`() {
        (0 until garisFont).forEach { i ->
            val nilai = tickValueOf(i, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont)
            assertEquals("garis $i", nilai, clampFontSize(nilai))
        }
        (0 until garisOffset).forEach { i ->
            val nilai = tickValueOf(i, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset)
            assertEquals("garis $i", nilai, clampLyricsOffsetMs(nilai))
        }
        (0 until garisSpasi).forEach { i ->
            val nilai = tickValueOf(i, LINE_SPACING_MIN, LINE_SPACING_MAX, garisSpasi)
            assertEquals("garis $i", nilai, clampLineSpacing(nilai))
        }
    }

    @Test
    fun `deterministik`() {
        repeat(5) {
            assertEquals(
                tickValueOf(7, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont),
                tickValueOf(7, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont),
            )
        }
    }

    // ── Membulatkan, bukan memotong ──────────────────────────────
    //
    // Ketiga rentang yang dipakai app kebetulan habis dibagi langkahnya, jadi
    // di sana pembulatan dan pemotongan menghasilkan angka yang sama persis.
    // Tes di bawah memakai rentang yang TIDAK habis dibagi, satu-satunya cara
    // membedakan keduanya.

    /**
     * REGRESI: dengan pemotongan, garis terakhir tidak pernah mewakili nilai
     * maksimum saat rentangnya tidak habis dibagi. Setelan jadi tidak bisa
     * disetel ke ujung atasnya sama sekali.
     */
    @Test
    fun `garis terakhir tetap nilai maksimum di rentang yang tidak habis dibagi`() {
        // 0..10 dengan 4 garis: langkahnya 3,33 dan tidak bulat.
        assertEquals(0, tickValueOf(0, 0, 10, 4))
        assertEquals("0,333 * 10 = 3,33 -> 3", 3, tickValueOf(1, 0, 10, 4))
        assertEquals("0,667 * 10 = 6,67 -> 7, dipotong akan jadi 6", 7, tickValueOf(2, 0, 10, 4))
        assertEquals(10, tickValueOf(3, 0, 10, 4))
    }

    @Test
    fun `nilai garis dibulatkan ke terdekat bukan ke bawah`() {
        // 0..100 dengan 3 garis: tengahnya 50, ujungnya 100.
        assertEquals(50, tickValueOf(1, 0, 100, 3))
        // 0..7 dengan 3 garis: tengahnya 3,5 dan harus naik ke 4.
        assertEquals("3,5 harus naik ke 4", 4, tickValueOf(1, 0, 7, 3))
    }

    /**
     * REGRESI: dengan pemotongan, sentuhan selalu menempel ke garis di
     * KIRInya. Menyentuh tepat di sebelah kiri sebuah garis akan melompat
     * mundur satu garis, dan slider terasa seperti selalu meleset.
     */
    @Test
    fun `sentuhan menempel ke garis terdekat, bukan selalu ke kiri`() {
        // 0,49 * 20 = 9,8. Membulat ke 10; memotong akan jadi 9.
        assertEquals(10, tickIndexAt(0.49f, 21))
        // 0,51 * 20 = 10,2. Keduanya menghasilkan 10, jadi ini bukan pembeda,
        // tapi memastikan sisi kanannya tidak ikut bergeser.
        assertEquals(10, tickIndexAt(0.51f, 21))
        // Tepat di tengah dua garis: 0,525 * 20 = 10,5 -> naik ke 11.
        assertEquals(11, tickIndexAt(0.525f, 21))
    }

    @Test
    fun `sentuhan tepat sebelum garis terakhir tidak mundur`() {
        // 0,99 * 20 = 19,8 -> 20, bukan 19.
        assertEquals(20, tickIndexAt(0.99f, 21))
    }

    /**
     * Jarak terjauh antara posisi sentuh dan garis yang terpilih tidak boleh
     * lebih dari setengah jarak antargaris. Itu definisi "terdekat", dan
     * pemotongan melanggarnya sampai satu jarak penuh.
     */
    @Test
    fun `garis terpilih tidak pernah lebih jauh dari setengah jarak antargaris`() {
        val n = 21
        val jarak = 1f / (n - 1)
        (0..200).forEach { k ->
            val f = k / 200f
            val i = tickIndexAt(f, n)
            val posisiGaris = i.toFloat() / (n - 1)
            assertTrue(
                "f=$f memilih garis $i di $posisiGaris, terlalu jauh",
                kotlin.math.abs(posisiGaris - f) <= jarak / 2 + 0.001f,
            )
        }
    }
}
