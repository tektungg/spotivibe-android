package com.tglabs.spotivibe.domain

import java.util.Locale

/**
 * Debug report: semua yang perlu diketahui untuk mendiagnosis masalah sync
 * atau lirik di perangkat orang lain, sebagai teks polos untuk disalin.
 *
 * Tidak ada token di app ini lagi, tapi alasan gagal sumber lirik bisa memuat
 * URL lengkap dengan query. [redact] membuang query string dan nilai
 * key/token/secret sebelum teksnya keluar dari app.
 */

/** Bahan debug report, dikumpulkan lapisan data. Semua opsional selain identitas app. */
data class DebugInfo(
    val appVersion: String,
    val variant: String,
    val androidVersion: String,
    val device: String,
    val playerPackage: String? = null,
    val playerLabel: String? = null,
    val playerVersion: String? = null,
    val track: NowPlaying? = null,
    val controls: PlayerControls? = null,
    val offsets: SyncOffsets? = null,
    val lyricsState: LyricsState? = null,
    val report: SourceReport? = null,
    val online: Boolean = true,
    val communitySources: Boolean = true,
    val trace: List<TraceEntry> = emptyList(),
    /** Waktu `elapsedRealtime` saat report dibuat, untuk waktu relatif di jejak. */
    val nowMs: Long = 0L,
)

fun buildDebugReport(info: DebugInfo): String = redact(
    buildString {
        appendLine("Spotivibe debug report")
        appendLine("App: ${info.appVersion} (${info.variant})")
        appendLine("Android: ${info.androidVersion} · ${info.device}")
        appendLine("Network: ${if (info.online) "online" else "offline"} · community sources ${if (info.communitySources) "on" else "off"}")
        appendLine()
        appendPlayer(info)
        appendLyrics(info)
        appendTrace(info)
    }.trimEnd(),
)

private fun StringBuilder.appendPlayer(info: DebugInfo) {
    appendLine("── Player")
    if (info.playerPackage == null) {
        appendLine("none followed")
        appendLine()
        return
    }
    appendLine("${info.playerLabel ?: "?"} · ${info.playerPackage} · ${info.playerVersion ?: "?"}")
    info.track?.let { t ->
        appendLine("Track: ${t.title} — ${t.artist} (${t.album}) · ${t.durationMs} ms · ${t.id}")
        appendLine("Position: ${t.progressMs} ms · ${if (t.isPaused) "paused" else "playing"}")
    }
    info.controls?.let { c ->
        appendLine("Controls: play/pause=${c.canPlayPause} seek=${c.canSeek} next=${c.canSkipNext} prev=${c.canSkipPrevious}")
    }
    info.offsets?.let { o ->
        appendLine("Offsets: global ${o.globalMs} · device ${o.deviceMs} (${o.outputLabel ?: "?"}) · song ${o.songMs} · total ${o.totalMs} ms")
    }
    appendLine()
}

private fun StringBuilder.appendLyrics(info: DebugInfo) {
    appendLine("── Lyrics")
    appendLine("State: ${describeState(info.lyricsState)}")
    val report = info.report
    if (report == null) {
        appendLine("Sources: no network lookup for this track (cache or saved lyrics)")
    } else {
        appendLine("Winner: ${report.winner?.displayName ?: "none"}")
        report.entries.forEach { e -> appendLine("  ${e.id.displayName}: ${describeOutcome(e)}") }
    }
    appendLine()
}

private fun StringBuilder.appendTrace(info: DebugInfo) {
    appendLine("── Recent events (seconds before this report)")
    if (info.trace.isEmpty()) appendLine("none")
    info.trace.forEach { e ->
        val ago = (info.nowMs - e.atMs).coerceAtLeast(0L) / MS_PER_SECOND
        appendLine("  -%.1fs %s".format(Locale.ROOT, ago, e.message))
    }
}

private fun describeState(state: LyricsState?): String = when (state) {
    null -> "?"
    LyricsState.Loading -> "loading"
    LyricsState.NotFound -> "not found"
    LyricsState.Offline -> "offline, nothing saved"
    is LyricsState.Unavailable -> "unavailable (${state.reason})"
    is LyricsState.Ready -> "ready · ${timingTier(state.result).name.lowercase()} · ${state.result.synced?.size ?: 0} lines"
}

private fun describeOutcome(entry: SourceEntry): String = when (val o = entry.outcome) {
    is SourceOutcome.Found -> "found · ${o.tier.name.lowercase()}" + (entry.durationSec?.let { " · %.1f s".format(Locale.ROOT, it) } ?: "")
    SourceOutcome.NotFound -> "not found"
    is SourceOutcome.Failed -> "failed · ${describeFailure(o.reason)} · ${o.reason}"
    SourceOutcome.Skipped -> "skipped (cooling down)"
    SourceOutcome.NotAsked -> "not asked (race ended first)"
}

/** Buang query string URL dan nilai yang tampak seperti rahasia. */
fun redact(text: String): String = text
    .replace(URL_QUERY) { "${it.groupValues[1]}?…" }
    .replace(SECRET_VALUE) { "${it.groupValues[1]}=…" }

private val URL_QUERY = Regex("""(https?://[^\s?]+)\?[^\s)]*""")
private val SECRET_VALUE = Regex("""(?i)\b(api_?key|key|token|access_token|secret|password)=[^&\s]+""")
private const val MS_PER_SECOND = 1000.0
