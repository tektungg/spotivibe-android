package com.tglabs.spotivibe.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.tglabs.spotivibe.BuildConfig
import com.tglabs.spotivibe.domain.DebugInfo
import com.tglabs.spotivibe.domain.SyncTrace
import com.tglabs.spotivibe.domain.buildDebugReport

/**
 * Mengumpulkan bahan debug report dari bagian app yang butuh `Context`
 * (versi app player, info perangkat), lalu menyerahkannya ke
 * [buildDebugReport] yang murni.
 */
class DebugReportCollector(
    private val context: Context,
    private val controller: PlaybackController,
    private val source: PlaybackSource,
    private val trace: SyncTrace,
    private val communitySources: () -> Boolean,
) {

    fun collect(): String {
        val pkg = source.playerPackage.value
        return buildDebugReport(
            DebugInfo(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                variant = "${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE}",
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                playerPackage = pkg,
                playerLabel = source.playerLabel.value,
                playerVersion = pkg?.let(::versionOf),
                track = controller.track.value,
                controls = controller.controls.value,
                offsets = controller.syncOffsets.value,
                lyricsState = controller.lyricsState.value,
                report = controller.sourceReport.value,
                online = controller.isOnline.value,
                communitySources = communitySources(),
                trace = trace.snapshot(),
                nowMs = SystemClock.elapsedRealtime(),
            ),
        )
    }

    /** Versi app player, atau null kalau paketnya tidak terlihat (package visibility). */
    private fun versionOf(packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()
}
