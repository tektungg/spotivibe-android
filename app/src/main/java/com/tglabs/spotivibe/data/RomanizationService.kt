package com.tglabs.spotivibe.data

import android.util.Log
import com.atilika.kuromoji.ipadic.Tokenizer
import com.tglabs.spotivibe.domain.Script
import com.tglabs.spotivibe.domain.detectDocumentScript
import com.tglabs.spotivibe.domain.detectScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * Romanization service untuk lirik non-Latin: JA (Hepburn), KO (Revised
 * Romanization), ZH (Hanyu Pinyin dengan tone mark).
 *
 * Semua heavy engine (kuromoji tokenizer, pinyin formatter, kana table)
 * di-lazy init — bayar cost hanya kalau benar-benar dipanggil. Kuromoji init
 * paling mahal (~1-2s pertama kali, load IPADIC ~10MB dari JAR).
 *
 * Thread-safe untuk dipanggil concurrent dari multiple coroutine — semua
 * heavy work otomatis di-shift ke [Dispatchers.IO]. Lazy init via Kotlin
 * `by lazy` default-nya synchronized.
 *
 * Limitations:
 * - JA: tidak handle particle pronunciation special case (は→wa, を→o, へ→e).
 *   Untuk lirik biasanya OK karena reading dari kuromoji sudah kasih katakana
 *   yang benar (mis. は di particle context bisa tetap "ha" — diterima).
 * - KO: simplified Revised Romanization tanpa liaison rules antar-syllable
 *   (mis. 한국어 → "hangugeo" yang benar vs "hangukeo" yang kita produce).
 *   Tetap readable untuk K-pop lyrics.
 * - ZH: per-char lookup via pinyin4j. Heteronym (多音字) ambil reading pertama
 *   — kadang salah untuk konteks tertentu, tapi cukup untuk sing-along.
 */
class RomanizationService {

    // ---- Cache ------------------------------------------------------------------
    //
    // Romanisasi itu deterministik: teks yang sama dengan script yang sama selalu
    // menghasilkan keluaran yang sama. Sebelumnya tidak ada cache sama sekali,
    // jadi kuromoji dijalankan ulang setiap kali user mematikan lalu menyalakan
    // toggle romanisasi, dan setiap kali lagu yang sama diputar lagi, bahkan
    // saat liriknya sendiri datang dari disk cache.
    //
    // Key-nya (teks, script), BUKAN teks saja. Teks yang sama beda hasilnya
    // tergantung script: 運命 jadi "unmei" sebagai JA, "yun ming" sebagai ZH.
    //
    // Hasil null (baris kosong, baris Latin, atau gagal) SENGAJA tidak di-cache.
    // Ketiganya murah dideteksi ulang, dan tidak menyimpannya menghilangkan
    // ambiguitas antara "miss" dan "pernah dihitung, hasilnya null".

    private data class CacheKey(val text: String, val script: Script)

    private val cache = object : LinkedHashMap<CacheKey, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<CacheKey, String>?): Boolean =
            size > CACHE_MAX
    }

    @Volatile private var hits = 0L
    @Volatile private var misses = 0L

    // Ukuran batch terakhir. Dicatat terpisah dari hits/misses karena hits dan
    // misses bergantung penjadwalan coroutine: tanpa dedup, baris kembar yang
    // dilempar bersamaan bisa kebetulan saling menunggu dan menghasilkan hit.
    // Dua angka ini menggambarkan dedup secara langsung dan deterministik.
    @Volatile private var lastBatchTotal = 0
    @Volatile private var lastBatchDistinct = 0

    /** Angka untuk melihat cache-nya benar-benar bekerja. Dipakai log dan test. */
    data class Stats(
        val hits: Long,
        val misses: Long,
        val size: Int,
        /** Jumlah baris di panggilan [romanizeLines] terakhir. */
        val lastBatchTotal: Int,
        /** Berapa di antaranya yang benar-benar dikirim ke mesin romanisasi. */
        val lastBatchDistinct: Int,
    )

    fun stats(): Stats = synchronized(cache) {
        Stats(hits, misses, cache.size, lastBatchTotal, lastBatchDistinct)
    }

    fun clearCache() = synchronized(cache) {
        cache.clear()
        hits = 0
        misses = 0
        lastBatchTotal = 0
        lastBatchDistinct = 0
    }

    private fun cacheGet(key: CacheKey): String? = synchronized(cache) {
        val v = cache[key]
        if (v != null) hits++ else misses++
        v
    }

    private fun cachePut(key: CacheKey, value: String) = synchronized(cache) {
        cache[key] = value
    }

    // ---- JA (kuromoji + Hepburn) -------------------------------------------------

    private val tokenizer: Tokenizer by lazy {
        Log.d(TAG, "Initializing kuromoji tokenizer (one-time, ~1-2s)…")
        Tokenizer.Builder().build().also {
            Log.d(TAG, "kuromoji tokenizer ready")
        }
    }

    // ---- ZH (pinyin4j) -----------------------------------------------------------

    private val pinyinFormat: HanyuPinyinOutputFormat by lazy {
        HanyuPinyinOutputFormat().apply {
            caseType = HanyuPinyinCaseType.LOWERCASE
            toneType = HanyuPinyinToneType.WITH_TONE_MARK
            vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
        }
    }

    /**
     * Romanize satu baris memakai [script] yang sudah ditentukan untuk seluruh
     * lagu. Returns null kalau text kosong, barisnya murni Latin, atau ada
     * exception.
     *
     * [script] WAJIB datang dari [detectDocumentScript], bukan dari
     * `detectScript(text)` per baris. Baris Jepang yang isinya kanji saja akan
     * dinilai Mandarin kalau diputuskan per baris, dan keluar sebagai pinyin di
     * tengah lagu yang selebihnya romaji.
     *
     * Aman dipanggil dari main thread — internal sudah `withContext(IO)`.
     * Init kuromoji pertama kali bisa ~1-2 detik.
     */
    suspend fun romanize(text: String, script: Script): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        // Baris yang murni Latin tidak perlu diromanisasi, apa pun script lagunya.
        if (detectScript(text) == Script.LATIN) return@withContext null

        val key = CacheKey(text, script)
        cacheGet(key)?.let { return@withContext it }

        val result = try {
            when (script) {
                Script.JA -> romanizeJapanese(text).takeIf { it.isNotBlank() }
                Script.KO -> romanizeKorean(text).takeIf { it.isNotBlank() }
                Script.ZH -> romanizeChinese(text).takeIf { it.isNotBlank() }
                Script.LATIN -> null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "romanize() failed for line='${text.take(40)}…': ${t.message}", t)
            null
        }
        if (result != null) cachePut(key, result)
        result
    }

    /**
     * Batch romanize satu lagu. Script ditentukan SEKALI dari seluruh baris,
     * lalu dipakai konsisten untuk semuanya, supaya satu lagu tidak pernah
     * bercampur romaji dan pinyin.
     *
     * Baris kembar dihitung SEKALI. Reff yang berulang tiga kali dulu
     * memromanisasi teks yang sama tiga kali, dan karena semuanya dilempar ke
     * `async` bersamaan, cache pun tidak menolong: ketiganya miss sebelum ada
     * yang sempat menulis hasilnya. Dedup di depan menutup itu.
     *
     * Hasil list size sama persis dengan input (1:1 mapping, null untuk yang
     * gagal / Latin / blank).
     */
    suspend fun romanizeLines(texts: List<String>): List<String?> = coroutineScope {
        val script = detectDocumentScript(texts)
        if (script == Script.LATIN) return@coroutineScope texts.map { null }

        val before = stats()
        val unik = texts.distinct()
        lastBatchTotal = texts.size
        lastBatchDistinct = unik.size
        val hasil = unik.map { line ->
            async(Dispatchers.IO) { romanize(line, script) }
        }.awaitAll()
        val perTeks = unik.zip(hasil).toMap()

        val after = stats()
        Log.d(
            TAG,
            "Romaji $script: ${texts.size} baris, ${unik.size} unik, " +
                "${after.hits - before.hits} dari cache, " +
                "${after.misses - before.misses} dihitung (cache ${after.size})",
        )
        texts.map { perTeks[it] }
    }

    // ---- Japanese ----------------------------------------------------------------

    /**
     * Tokenize via kuromoji, ambil katakana reading per token, convert
     * katakana → hiragana → Hepburn romaji, lalu join dengan spasi.
     */
    private fun romanizeJapanese(text: String): String {
        val tokens = tokenizer.tokenize(text)
        val sb = StringBuilder()
        for ((i, tok) in tokens.withIndex()) {
            val surface = tok.surface
            // reading bisa null / "*" untuk token tidak dikenal (symbol, latin)
            val readingKatakana = tok.reading?.takeIf { it.isNotBlank() && it != "*" }

            val piece = if (readingKatakana != null) {
                hepburnFromKana(katakanaToHiragana(readingKatakana))
            } else {
                // Token non-Japanese (punctuation, ASCII, dll) — pass through
                surface
            }
            if (piece.isBlank()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(piece)
            // Suppress unused-i lint
            @Suppress("UNUSED_VARIABLE") val _i = i
        }
        return sb.toString().trim()
    }

    /** Katakana (U+30A1..U+30F6) → Hiragana (U+3041..U+3096) via offset -0x60. */
    private fun katakanaToHiragana(s: String): String {
        val out = StringBuilder(s.length)
        for (ch in s) {
            val code = ch.code
            // Range katakana yang punya counterpart hiragana
            if (code in 0x30A1..0x30F6) {
                out.append((code - 0x60).toChar())
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Hiragana → Hepburn romaji. Greedy 2-char match dulu (untuk yoon きゃ, しゅ,
     * dll), lalu fallback ke single char. Sokuon (っ) doubles next consonant.
     * Chouonpu (ー) extend previous vowel (atau di-skip kalau di awal).
     */
    private fun hepburnFromKana(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            // Sokuon っ — double konsonan token berikutnya
            if (ch == 'っ') {
                if (i + 1 < s.length) {
                    val nextRomaji = lookupKanaAt(s, i + 1)
                    if (nextRomaji != null) {
                        val (romaji, consumed) = nextRomaji
                        if (romaji.isNotEmpty() && romaji[0] !in "aiueo") {
                            // Special: ch → tch (Hepburn convention)
                            if (romaji.startsWith("ch")) out.append("t")
                            else out.append(romaji[0])
                        }
                        out.append(romaji)
                        i += 1 + consumed
                        continue
                    }
                }
                i++
                continue
            }
            // Chouonpu ー — perpanjang vowel sebelumnya
            if (ch == 'ー') {
                if (out.isNotEmpty()) {
                    val last = out.last()
                    if (last in "aiueo") out.append(last)
                }
                i++
                continue
            }

            val match = lookupKanaAt(s, i)
            if (match != null) {
                out.append(match.first)
                i += match.second
            } else {
                // Bukan hiragana yang dikenal — pass through (mungkin punctuation
                // atau katakana sisa yang tidak ter-convert)
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    /**
     * Try 2-char match dari position [pos], fallback ke 1-char. Returns
     * Pair(romaji, charsConsumed) atau null kalau tidak match sama sekali.
     */
    private fun lookupKanaAt(s: String, pos: Int): Pair<String, Int>? {
        if (pos + 1 < s.length) {
            val two = s.substring(pos, pos + 2)
            HIRAGANA_TO_ROMAJI[two]?.let { return it to 2 }
        }
        val one = s.substring(pos, pos + 1)
        HIRAGANA_TO_ROMAJI[one]?.let { return it to 1 }
        return null
    }

    // ---- Korean ------------------------------------------------------------------

    /**
     * Decompose Hangul syllable U+AC00..U+D7A3 → jamo, lookup Revised
     * Romanization arrays. Non-Hangul char (Latin, punctuation, spasi)
     * pass-through unchanged.
     */
    private fun romanizeKorean(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val code = ch.code
            if (code in 0xAC00..0xD7A3) {
                val idx = code - 0xAC00
                val cho = idx / (21 * 28)
                val jung = (idx % (21 * 28)) / 28
                val jong = idx % 28
                sb.append(CHOSEONG[cho])
                sb.append(JUNGSEONG[jung])
                sb.append(JONGSEONG[jong])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    // ---- Chinese -----------------------------------------------------------------

    /**
     * Per-char pinyin lookup. Pinyin4j API per-char only (BUKAN per-string),
     * jadi kita iterasi manual. Heteronym ambil reading pertama dari array.
     */
    private fun romanizeChinese(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.code in 0x4E00..0x9FFF) {
                val pinyins: Array<String>? = runCatching {
                    PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)
                }.getOrNull()
                val py = pinyins?.firstOrNull()
                if (py != null) {
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    sb.append(py)
                    sb.append(' ')
                } else {
                    sb.append(ch)
                }
            } else {
                sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private const val TAG = "RomanizationService"

        /**
         * Baris lirik pendek, jadi entry-nya murah. 2000 baris kira-kira 25
         * lagu, cukup untuk satu playlist tanpa membuang hasil yang masih
         * mungkin dipakai lagi.
         */
        private const val CACHE_MAX = 2000

        // ---- Korean Revised Romanization tables --------------------------------

        private val CHOSEONG = arrayOf(
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
            "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
        )
        private val JUNGSEONG = arrayOf(
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
            "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
        )
        private val JONGSEONG = arrayOf(
            "", "k", "kk", "ks", "n", "nj", "nh", "t", "l", "lk",
            "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "s",
            "ss", "ng", "j", "ch", "k", "t", "p", "h"
        )

        // ---- Hiragana → Hepburn romaji table -----------------------------------
        //
        // Sumber: standard Hepburn revised. Termasuk gojuon + dakuten + handakuten
        // + yoon (combined kana). ~110 entries. n+vowel handled via single ん entry.
        private val HIRAGANA_TO_ROMAJI: Map<String, String> = buildMap {
            // Vowels
            put("あ", "a"); put("い", "i"); put("う", "u"); put("え", "e"); put("お", "o")

            // K row
            put("か", "ka"); put("き", "ki"); put("く", "ku"); put("け", "ke"); put("こ", "ko")
            // G row (dakuten)
            put("が", "ga"); put("ぎ", "gi"); put("ぐ", "gu"); put("げ", "ge"); put("ご", "go")

            // S row
            put("さ", "sa"); put("し", "shi"); put("す", "su"); put("せ", "se"); put("そ", "so")
            // Z row
            put("ざ", "za"); put("じ", "ji"); put("ず", "zu"); put("ぜ", "ze"); put("ぞ", "zo")

            // T row
            put("た", "ta"); put("ち", "chi"); put("つ", "tsu"); put("て", "te"); put("と", "to")
            // D row
            put("だ", "da"); put("ぢ", "ji"); put("づ", "zu"); put("で", "de"); put("ど", "do")

            // N row
            put("な", "na"); put("に", "ni"); put("ぬ", "nu"); put("ね", "ne"); put("の", "no")

            // H row
            put("は", "ha"); put("ひ", "hi"); put("ふ", "fu"); put("へ", "he"); put("ほ", "ho")
            // B row (dakuten)
            put("ば", "ba"); put("び", "bi"); put("ぶ", "bu"); put("べ", "be"); put("ぼ", "bo")
            // P row (handakuten)
            put("ぱ", "pa"); put("ぴ", "pi"); put("ぷ", "pu"); put("ぺ", "pe"); put("ぽ", "po")

            // M row
            put("ま", "ma"); put("み", "mi"); put("む", "mu"); put("め", "me"); put("も", "mo")

            // Y row
            put("や", "ya"); put("ゆ", "yu"); put("よ", "yo")

            // R row
            put("ら", "ra"); put("り", "ri"); put("る", "ru"); put("れ", "re"); put("ろ", "ro")

            // W row
            put("わ", "wa"); put("ゐ", "wi"); put("ゑ", "we"); put("を", "o")

            // N (syllabic)
            put("ん", "n")

            // Yoon — K/G
            put("きゃ", "kya"); put("きゅ", "kyu"); put("きょ", "kyo")
            put("ぎゃ", "gya"); put("ぎゅ", "gyu"); put("ぎょ", "gyo")
            // Yoon — S/Z (sh / j)
            put("しゃ", "sha"); put("しゅ", "shu"); put("しょ", "sho"); put("しぇ", "she")
            put("じゃ", "ja"); put("じゅ", "ju"); put("じょ", "jo"); put("じぇ", "je")
            // Yoon — T/D (ch)
            put("ちゃ", "cha"); put("ちゅ", "chu"); put("ちょ", "cho"); put("ちぇ", "che")
            put("ぢゃ", "ja"); put("ぢゅ", "ju"); put("ぢょ", "jo")
            // Yoon — N
            put("にゃ", "nya"); put("にゅ", "nyu"); put("にょ", "nyo")
            // Yoon — H/B/P
            put("ひゃ", "hya"); put("ひゅ", "hyu"); put("ひょ", "hyo")
            put("びゃ", "bya"); put("びゅ", "byu"); put("びょ", "byo")
            put("ぴゃ", "pya"); put("ぴゅ", "pyu"); put("ぴょ", "pyo")
            // Yoon — M
            put("みゃ", "mya"); put("みゅ", "myu"); put("みょ", "myo")
            // Yoon — R
            put("りゃ", "rya"); put("りゅ", "ryu"); put("りょ", "ryo")

            // Foreign-sound combos (common in modern lyrics) — katakana-origin
            // tapi tetap masuk lewat path hiragana setelah conversion.
            put("ふぁ", "fa"); put("ふぃ", "fi"); put("ふぇ", "fe"); put("ふぉ", "fo")
            put("うぃ", "wi"); put("うぇ", "we"); put("うぉ", "wo")
            put("ゔぁ", "va"); put("ゔぃ", "vi"); put("ゔ", "vu"); put("ゔぇ", "ve"); put("ゔぉ", "vo")
            put("てぃ", "ti"); put("でぃ", "di"); put("とぅ", "tu"); put("どぅ", "du")

            // Small kana standalone (kalau muncul tanpa pair) — fallback
            put("ぁ", "a"); put("ぃ", "i"); put("ぅ", "u"); put("ぇ", "e"); put("ぉ", "o")
            put("ゃ", "ya"); put("ゅ", "yu"); put("ょ", "yo")
        }
    }
}
