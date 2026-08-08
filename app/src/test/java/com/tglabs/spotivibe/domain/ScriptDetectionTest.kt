package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectionTest {

    // ── detectScript: per baris ───────────────────────────────────

    @Test
    fun `hiragana terbaca Jepang`() {
        assertEquals(Script.JA, detectScript("こんにちは"))
    }

    @Test
    fun `katakana terbaca Jepang`() {
        assertEquals(Script.JA, detectScript("コンニチハ"))
    }

    @Test
    fun `kana bercampur kanji terbaca Jepang`() {
        assertEquals(Script.JA, detectScript("君の名前"))
    }

    @Test
    fun `hangul terbaca Korea`() {
        assertEquals(Script.KO, detectScript("안녕하세요"))
    }

    @Test
    fun `hanzi tanpa kana terbaca Mandarin`() {
        assertEquals(Script.ZH, detectScript("我爱你"))
    }

    @Test
    fun `latin terbaca Latin`() {
        assertEquals(Script.LATIN, detectScript("I love you"))
        assertEquals(Script.LATIN, detectScript(""))
        assertEquals(Script.LATIN, detectScript("Ooh, yeah! (x2)"))
    }

    /**
     * Ini BUKAN bug di detectScript, ini batas kemampuannya, dan justru alasan
     * detectDocumentScript ada. Kanji dan hanzi memakai blok Unicode yang sama,
     * jadi satu baris tanpa kana memang tidak bisa dibedakan.
     */
    @Test
    fun `baris kanji saja memang ambigu dan terbaca Mandarin`() {
        assertEquals(Script.ZH, detectScript("運命"))
    }

    // ── detectDocumentScript: per lagu ───────────────────────────

    /**
     * REGRESI: inti perbaikan ini.
     *
     * Lagu Jepang hampir selalu punya baris yang isinya kanji saja. Dengan
     * keputusan per baris, baris itu keluar sebagai pinyin sementara baris
     * lainnya keluar sebagai romaji, jadi satu lagu bercampur dua sistem
     * romanisasi.
     */
    @Test
    fun `lagu Jepang dengan baris kanji saja tetap dinilai Jepang`() {
        val lagu = listOf(
            "君の名前を呼ぶ",   // ada kana
            "運命",              // kanji SAJA -- yang dulu jadi pinyin
            "夜明け前",          // ada kana
        )
        assertEquals(Script.JA, detectDocumentScript(lagu))
        assertEquals(
            "baris ambigu ini harus ikut keputusan lagu",
            Script.ZH,
            detectScript(lagu[1]),
        )
        assertNotEquals(
            "keputusan lagu harus mengalahkan keputusan per baris",
            detectScript(lagu[1]),
            detectDocumentScript(lagu),
        )
    }

    @Test
    fun `satu baris berkana saja sudah cukup menentukan seluruh lagu`() {
        val lagu = listOf("運命", "未来", "希望", "そして", "光")
        assertEquals(Script.JA, detectDocumentScript(lagu))
    }

    @Test
    fun `lagu Mandarin murni tetap Mandarin`() {
        val lagu = listOf("我爱你", "月亮代表我的心", "永远")
        assertEquals(Script.ZH, detectDocumentScript(lagu))
    }

    @Test
    fun `lagu Korea tetap Korea walau ada hanja`() {
        val lagu = listOf("사랑해", "運命", "너의 이름")
        assertEquals(Script.KO, detectDocumentScript(lagu))
    }

    @Test
    fun `lagu Latin murni dinilai Latin`() {
        assertEquals(
            Script.LATIN,
            detectDocumentScript(listOf("Hello world", "I love you", "")),
        )
    }

    @Test
    fun `daftar kosong dinilai Latin`() {
        assertEquals(Script.LATIN, detectDocumentScript(emptyList()))
    }

    @Test
    fun `baris Latin di tengah lagu Jepang tidak mengubah keputusan`() {
        val lagu = listOf("Oh baby", "君を想う", "yeah yeah", "運命")
        assertEquals(Script.JA, detectDocumentScript(lagu))
    }

    /** K-pop lazim mencampur Inggris dan Korea. Hangul tetap menentukan. */
    @Test
    fun `lagu campuran Inggris dan Korea dinilai Korea`() {
        val lagu = listOf("Let's go", "너와 나", "one two three", "우리")
        assertEquals(Script.KO, detectDocumentScript(lagu))
    }

    @Test
    fun `kana lebih banyak daripada hangul menang`() {
        val lagu = listOf("こんにちは", "さようなら", "안녕")
        assertEquals(Script.JA, detectDocumentScript(lagu))
    }

    @Test
    fun `hangul lebih banyak daripada kana menang`() {
        val lagu = listOf("안녕하세요", "사랑해", "우리", "こんにちは")
        assertEquals(Script.KO, detectDocumentScript(lagu))
    }

    @Test
    fun `seri antara kana dan hangul dimenangkan kana secara deterministik`() {
        val lagu = listOf("こんにちは", "안녕하세요")
        assertEquals(Script.JA, detectDocumentScript(lagu))
        // Dipanggil berkali-kali harus tetap sama.
        repeat(5) { assertEquals(Script.JA, detectDocumentScript(lagu)) }
    }

    /**
     * Jamo terpisah SENGAJA tidak dihitung sebagai Hangul, karena
     * romanizeKorean cuma bisa memecah suku kata prakomposisi U+AC00..U+D7A3.
     * Melabelinya KO akan menghasilkan output yang tidak bisa diromanisasi.
     */
    @Test
    fun `jamo terpisah tidak dianggap Hangul`() {
        assertEquals(Script.LATIN, detectScript("ㄱㄴㄷ"))
    }

    // ── hasRomanizableText ───────────────────────────────────────

    @Test
    fun `lagu Latin murni tidak bisa diromanisasi`() {
        assertFalse(hasRomanizableText(listOf("Hello", "world")))
    }

    @Test
    fun `satu baris non-Latin sudah membuat lagu bisa diromanisasi`() {
        assertTrue(hasRomanizableText(listOf("Hello", "運命", "world")))
    }

    /**
     * Konsistensi antar dua fungsi: kalau ada yang bisa diromanisasi, script
     * lagunya tidak boleh LATIN, karena itu akan membuat romanizeLines
     * mengembalikan null untuk semua baris padahal tombolnya ditampilkan.
     */
    @Test
    fun `hasRomanizableText dan detectDocumentScript tidak saling bertentangan`() {
        val kasus = listOf(
            listOf("Hello", "world"),
            listOf("運命"),
            listOf("こんにちは", "Hello"),
            listOf("안녕", "運命"),
            listOf(""),
            emptyList(),
        )
        kasus.forEach { lagu ->
            val bisa = hasRomanizableText(lagu)
            val script = detectDocumentScript(lagu)
            assertEquals(
                "lagu=$lagu bisa=$bisa script=$script",
                bisa,
                script != Script.LATIN,
            )
        }
    }
}
