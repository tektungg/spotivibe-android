package com.tglabs.spotivibe.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Satu klien HTTP untuk semua sumber lirik.
 *
 * Dipakai bersama supaya semua sumber berbagi connection pool dan dispatcher
 * yang sama, dan semuanya mengirim User-Agent yang sama. API komunitas gratis
 * meminta klien memperkenalkan diri; request tanpa identitas lebih cepat
 * dibatasi.
 */
object HttpClients {

    /** Identitas app di setiap request ke sumber lirik. */
    const val USER_AGENT = "spotivibe-android (https://github.com/tektungg/spotivibe)"

    private const val CONNECT_TIMEOUT_S = 5L
    private const val READ_TIMEOUT_S = 10L

    val lyrics: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
}
