package com.tglabs.spotivibe.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface untuk LRCLIB public API.
 *
 * Base URL: https://lrclib.net
 *
 * Dokumentasi: https://lrclib.net/docs
 *
 * Catatan:
 * - `/api/get` mengembalikan 404 (dengan body) saat track tidak ketemu — kita
 *   bungkus dengan [Response] supaya bisa cek `isSuccessful` tanpa exception.
 * - `/api/search` selalu return 200 dengan array (bisa kosong).
 */
interface LrclibApi {

    /**
     * Exact match lookup. Lebih cepat dan akurat kalau metadata lengkap.
     *
     * @param duration durasi lagu dalam detik (integer). LRCLIB pakai field ini
     *   untuk verifikasi match — kalau beda > beberapa detik bisa 404.
     */
    @GET("/api/get")
    suspend fun get(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") duration: Int,
    ): Response<LrclibDto>

    /**
     * Fuzzy search berdasarkan title + artist saja. Fallback kalau `/get` 404.
     * Return array — caller pilih item pertama yang punya syncedLyrics
     * (atau item pertama saja kalau tidak ada yang synced).
     */
    @GET("/api/search")
    suspend fun search(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
    ): Response<List<LrclibDto>>
}
