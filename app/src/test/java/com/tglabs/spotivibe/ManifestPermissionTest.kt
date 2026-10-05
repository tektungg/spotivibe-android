package com.tglabs.spotivibe

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Penjaga: API yang butuh permission harus punya deklarasinya di manifest.
 *
 * Ada karena `Haptics.tick` memanggil `Vibrator.vibrate()` sementara manifest
 * tidak pernah mendeklarasikan `VIBRATE`. Kegagalannya sempurna tak terlihat:
 * `vibrate()` melempar SecurityException, `runCatching` di Haptics menelannya,
 * dan setelan haptic di Settings tampak menyala sambil tidak pernah bekerja.
 * Tidak ada test yang gagal, tidak ada crash, tidak ada log.
 *
 * Lint Android menangkapnya, tapi lint jalan di CI dan makan menit. Ini jalan
 * di lane cepat, sehingga salahnya ketahuan sebelum di-commit.
 */
class ManifestPermissionTest {

    /** Penanda pemakaian API -> permission yang diwajibkannya. */
    private val wajib = mapOf(
        ".vibrate(" to "android.permission.VIBRATE",
        "startForeground(" to "android.permission.FOREGROUND_SERVICE",
        "TYPE_APPLICATION_OVERLAY" to "android.permission.SYSTEM_ALERT_WINDOW",
        "registerDefaultNetworkCallback" to "android.permission.ACCESS_NETWORK_STATE",
    )

    private fun akar(): File = listOf(File("src/main"), File("app/src/main"))
        .firstOrNull { it.isDirectory }
        ?: run { fail("src/main tidak ketemu dari ${File(".").absolutePath}"); File(".") }

    private val manifest: String by lazy {
        val f = File(akar(), "AndroidManifest.xml")
        assertTrue("AndroidManifest.xml tidak ketemu di ${f.absolutePath}", f.isFile)
        f.readText()
    }

    private val sumber: List<File> by lazy {
        File(akar(), "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `sumber dan manifest terbaca supaya penjaga ini tidak lolos kosong`() {
        assertTrue("harus memindai sesuatu, dapat ${sumber.size} berkas", sumber.size > 5)
        assertTrue("manifest kosong", manifest.contains("<manifest"))
    }

    @Test
    fun `setiap API berpermission punya deklarasinya di manifest`() {
        val kurang = wajib.entries.mapNotNull { (penanda, permission) ->
            val pemakai = sumber.filter { penanda in it.readText() }
            when {
                pemakai.isEmpty() -> null
                manifest.contains("\"$permission\"") -> null
                else -> "$permission dibutuhkan oleh ${pemakai.joinToString { it.name }}"
            }
        }

        assertTrue(
            "API dipakai tanpa permission di AndroidManifest.xml. Kegagalannya " +
                "berupa SecurityException saat runtime, bukan error compile:\n" +
                kurang.joinToString("\n"),
            kurang.isEmpty(),
        )
    }

    /**
     * Sisi sebaliknya. Permission yang tidak dipakai lagi tetap tampil di
     * halaman izin Play Store dan di layar detail app, jadi pengguna melihat app
     * meminta sesuatu yang tidak pernah disentuhnya.
     */
    @Test
    fun `VIBRATE hanya dideklarasikan kalau memang dipakai`() {
        val dideklarasikan = manifest.contains("\"android.permission.VIBRATE\"")
        val dipakai = sumber.any { ".vibrate(" in it.readText() }
        assertTrue(
            "VIBRATE dideklarasikan=$dideklarasikan tapi dipakai=$dipakai",
            dideklarasikan == dipakai,
        )
    }
}
