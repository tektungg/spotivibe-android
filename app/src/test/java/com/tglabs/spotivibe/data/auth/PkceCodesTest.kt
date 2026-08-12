package com.tglabs.spotivibe.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PkceCodesTest {

    /** Sumber acak yang bisa diramal, supaya generate bisa diuji persis. */
    private class UrutanTetap(private val urutan: IntArray) : SecureRandom() {
        private var i = 0
        override fun nextInt(bound: Int): Int = urutan[i++ % urutan.size].mod(bound)
    }

    private val unreserved =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toSet()

    // ── Test vector resmi ────────────────────────────────────────

    /**
     * RFC 7636 appendix B. Kalau ini meleset, Spotify akan menolak setiap
     * penukaran code, dan gejalanya di app cuma "login gagal" tanpa petunjuk
     * bahwa challenge-nya yang salah.
     */
    @Test
    fun `challenge cocok dengan test vector RFC 7636`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceCodes.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `method challenge itu S256`() {
        assertEquals("S256", PkceCodes.CHALLENGE_METHOD)
    }

    // ── Bentuk challenge ─────────────────────────────────────────

    /**
     * BASE64URL tanpa padding. Padding `=` dan karakter `+` `/` dari base64
     * biasa akan rusak saat masuk query string dan membuat Spotify menolaknya.
     */
    @Test
    fun `challenge base64url tanpa padding`() {
        val c = PkceCodes.challengeFor("verifier-apa-saja-yang-penting-panjang")
        assertTrue("tidak boleh ada padding: $c", !c.contains("="))
        assertTrue("tidak boleh ada plus: $c", !c.contains("+"))
        assertTrue("tidak boleh ada garis miring: $c", !c.contains("/"))
        assertEquals("SHA-256 base64url selalu 43 karakter", 43, c.length)
    }

    @Test
    fun `challenge deterministik untuk verifier yang sama`() {
        val v = "verifier-yang-sama-persis-untuk-dua-kali-panggil"
        assertEquals(PkceCodes.challengeFor(v), PkceCodes.challengeFor(v))
    }

    @Test
    fun `verifier berbeda menghasilkan challenge berbeda`() {
        assertNotEquals(PkceCodes.challengeFor("satu"), PkceCodes.challengeFor("dua"))
    }

    // ── Verifier ─────────────────────────────────────────────────

    @Test
    fun `verifier hanya memakai charset unreserved`() {
        val v = PkceCodes.generate().verifier
        v.forEach { assertTrue("karakter terlarang '$it' di $v", it in unreserved) }
    }

    @Test
    fun `panjang bawaan sesuai dan dalam rentang RFC`() {
        val v = PkceCodes.generate().verifier
        assertEquals(PkceCodes.DEFAULT_VERIFIER_LENGTH, v.length)
        assertTrue(v.length in PkceCodes.MIN_VERIFIER_LENGTH..PkceCodes.MAX_VERIFIER_LENGTH)
    }

    @Test
    fun `panjang batas diterima`() {
        assertEquals(43, PkceCodes.generate(PkceCodes.MIN_VERIFIER_LENGTH).verifier.length)
        assertEquals(128, PkceCodes.generate(PkceCodes.MAX_VERIFIER_LENGTH).verifier.length)
    }

    /** Di luar 43..128 Spotify menolak, jadi lebih baik gagal di sini. */
    @Test
    fun `panjang di luar rentang RFC ditolak`() {
        listOf(0, 1, 42, 129, 1000, -5).forEach { panjang ->
            try {
                PkceCodes.generate(panjang)
                throw AssertionError("panjang $panjang seharusnya ditolak")
            } catch (e: IllegalArgumentException) {
                // memang ini yang diharapkan
            }
        }
    }

    @Test
    fun `challenge di dalam pasangan cocok dengan verifiernya`() {
        val p = PkceCodes.generate()
        assertEquals(PkceCodes.challengeFor(p.verifier), p.challenge)
    }

    @Test
    fun `sumber acak yang sama menghasilkan verifier yang sama`() {
        val a = PkceCodes.generate(43, UrutanTetap(intArrayOf(0, 1, 2, 3)))
        val b = PkceCodes.generate(43, UrutanTetap(intArrayOf(0, 1, 2, 3)))
        assertEquals(a, b)
    }

    @Test
    fun `dua pembangkitan nyata tidak pernah sama`() {
        val hasil = (1..50).map { PkceCodes.generate().verifier }.toSet()
        assertEquals("verifier harus unik tiap kali", 50, hasil.size)
    }

    // ── State ────────────────────────────────────────────────────

    @Test
    fun `state memakai charset dan panjang yang diminta`() {
        val s = PkceCodes.randomState(32)
        assertEquals(32, s.length)
        s.forEach { assertTrue("karakter terlarang '$it'", it in unreserved) }
    }

    @Test
    fun `state harus unik antar pembangkitan`() {
        val hasil = (1..50).map { PkceCodes.randomState() }.toSet()
        assertEquals(50, hasil.size)
    }

    @Test
    fun `panjang state nol ditolak`() {
        listOf(0, -1).forEach { panjang ->
            try {
                PkceCodes.randomState(panjang)
                throw AssertionError("panjang $panjang seharusnya ditolak")
            } catch (e: IllegalArgumentException) {
                // memang ini yang diharapkan
            }
        }
    }
}
