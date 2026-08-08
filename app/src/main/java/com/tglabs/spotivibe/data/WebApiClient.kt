package com.tglabs.spotivibe.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Spotify Web API wrapper untuk fitur yang App Remote tidak handle:
 * - GET /v1/me — product field untuk deteksi kemampuan kontrol
 * - GET /v1/me/player/queue — next tracks untuk lyrics preload
 *
 * [tokenProvider] sengaja `suspend`: token diambil lewat
 * [com.tglabs.spotivibe.data.auth.SpotifyAuthRepository.validAccessToken],
 * yang me-refresh sendiri saat sudah dekat expiry. Versi lama pakai getter
 * sinkron ke token implicit-grant yang tidak bisa diperbarui, jadi setelah
 * satu jam semua panggilan di sini return 401 selamanya.
 */
class WebApiClient(private val tokenProvider: suspend () -> String?) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Returns "premium" / "free" / "open" / null kalau gagal.
     */
    suspend fun getProduct(): String? = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext null
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "/me failed: ${res.code}")
                    return@withContext null
                }
                val body = res.body?.string() ?: return@withContext null
                JSONObject(body).optString("product").takeIf { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "/me threw: ${t.message}")
            null
        }
    }

    /** Lightweight info untuk preload — cuma yang dibutuhkan untuk fetchLyrics. */
    data class TrackInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
    )

    /**
     * Returns next N tracks in queue. Empty list kalau gagal atau queue empty.
     * `currentlyPlaying` field di-skip; kita pakai yang di App Remote saja.
     */
    suspend fun getQueue(limit: Int = 3): List<TrackInfo> = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/player/queue")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "/queue failed: ${res.code}")
                    return@withContext emptyList()
                }
                val body = res.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val queueArr = json.optJSONArray("queue") ?: return@withContext emptyList()
                val result = mutableListOf<TrackInfo>()
                for (i in 0 until minOf(queueArr.length(), limit)) {
                    val item = queueArr.getJSONObject(i)
                    val artists = item.optJSONArray("artists")
                    val firstArtist = if (artists != null && artists.length() > 0) {
                        artists.getJSONObject(0).optString("name", "")
                    } else ""
                    val album = item.optJSONObject("album")?.optString("name", "") ?: ""
                    result += TrackInfo(
                        id = item.optString("uri", ""),
                        title = item.optString("name", ""),
                        artist = firstArtist,
                        album = album,
                        durationMs = item.optLong("duration_ms", 0L),
                    )
                }
                result
            }
        } catch (t: Throwable) {
            Log.w(TAG, "/queue threw: ${t.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "WebApiClient"
    }
}
