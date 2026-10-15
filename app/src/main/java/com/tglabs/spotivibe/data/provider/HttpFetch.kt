package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.parseRetryAfterMs
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.cancellation.CancellationException

/** Satu respons HTTP yang sudah dibaca isinya. */
internal data class Fetched(val code: Int, val body: String, val retryAfterHeader: String?) {
    val isSuccessful: Boolean get() = code in HTTP_OK_RANGE
}

/**
 * GET [url] dan baca body-nya sebagai teks.
 *
 * Lewat `enqueue`, bukan `execute`, supaya pembatalan coroutine ikut
 * membatalkan request: sumber yang kalah perlombaan berhenti memakai jaringan
 * saat itu juga, bukan menunggu timeout-nya sendiri.
 */
internal suspend fun OkHttpClient.fetch(url: HttpUrl): Fetched {
    val call = newCall(Request.Builder().url(url).get().build())
    val response = call.await()
    return response.use { r -> Fetched(r.code, r.body?.string().orEmpty(), r.header("Retry-After")) }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, r, _ -> r.close() }
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
    })
}

/**
 * Status yang tidak sukses jadi probe. 404 adalah jawaban "tidak ada" yang
 * sungguhan; status lain (429, 5xx, 403) bisa berubah sebentar lagi.
 */
internal fun Fetched.failureProbe(): ProviderProbe =
    if (code == HTTP_NOT_FOUND) ProviderProbe.Absent
    else ProviderProbe.Failed("http_$code", parseRetryAfterMs(retryAfterHeader))

/**
 * Jalankan [block], ubah exception biasa jadi [ProviderProbe.Failed].
 * [CancellationException] dilempar ulang supaya pembatalan tetap bekerja.
 */
internal suspend fun probeCatching(label: String, block: suspend () -> ProviderProbe): ProviderProbe =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ProviderProbe.Failed("$label: ${t.javaClass.simpleName} ${t.message.orEmpty()}".trim())
    }

private val HTTP_OK_RANGE = 200..299
private const val HTTP_NOT_FOUND = 404
