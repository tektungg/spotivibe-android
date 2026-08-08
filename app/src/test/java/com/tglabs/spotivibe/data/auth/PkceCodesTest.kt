package com.tglabs.spotivibe.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceCodesTest {

    /**
     * Test vector RFC 7636 appendix B. Kalau ini gagal, penukaran code akan
     * ditolak Spotify dan tidak ada satu pun request Web API yang jalan.
     */
    @Test
    fun `challenge cocok dengan test vector RFC 7636`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceCodes.challengeFor(verifier),
        )
    }

    @Test
    fun `challenge base64url tanpa padding`() {
        val challenge = PkceCodes.generate().challenge
        assertTrue("padding '=' tidak boleh ada", '=' !in challenge)
        assertTrue("'+' bukan alfabet base64url", '+' !in challenge)
        assertTrue("'/' bukan alfabet base64url", '/' !in challenge)
        // SHA-256 = 32 byte -> 43 karakter base64 tanpa padding
        assertEquals(43, challenge.length)
    }

    @Test
    fun `verifier hanya memakai karakter unreserved`() {
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        repeat(50) {
            val verifier = PkceCodes.generate().verifier
            verifier.forEach { ch ->
                assertTrue("karakter '$ch' di luar charset unreserved", ch in allowed)
            }
        }
    }

    @Test
    fun `panjang verifier default masuk rentang yang diizinkan`() {
        val verifier = PkceCodes.generate().verifier
        assertTrue(verifier.length in PkceCodes.MIN_VERIFIER_LENGTH..PkceCodes.MAX_VERIFIER_LENGTH)
    }

    @Test
    fun `panjang di batas rentang diterima`() {
        assertEquals(43, PkceCodes.generate(length = 43).verifier.length)
        assertEquals(128, PkceCodes.generate(length = 128).verifier.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `verifier terlalu pendek ditolak`() {
        PkceCodes.generate(length = 42)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `verifier terlalu panjang ditolak`() {
        PkceCodes.generate(length = 129)
    }

    @Test
    fun `tiap generate menghasilkan verifier berbeda`() {
        val seen = HashSet<String>()
        repeat(200) { seen.add(PkceCodes.generate().verifier) }
        assertEquals("verifier tidak boleh berulang", 200, seen.size)
    }

    @Test
    fun `state acak dan panjangnya sesuai`() {
        val a = PkceCodes.randomState()
        val b = PkceCodes.randomState()
        assertEquals(32, a.length)
        assertNotEquals(a, b)
    }

    @Test
    fun `challenge deterministik untuk verifier yang sama`() {
        val codes = PkceCodes.generate()
        assertEquals(codes.challenge, PkceCodes.challengeFor(codes.verifier))
    }
}
