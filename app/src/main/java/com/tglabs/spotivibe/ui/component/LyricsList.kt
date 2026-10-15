package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.interludeGapMs
import com.tglabs.spotivibe.domain.introGapMs
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import com.tglabs.spotivibe.util.Haptics

/**
 * Editorial lyrics list. Newsreader serif typography:
 * - Active line: SvType.LyricActive (Medium 30sp), color = accent
 * - Near lines (idx ±1): LyricPassive 22sp, color = ink2 + 85% alpha
 * - Far lines (idx ±2): smaller 18sp, color = ink3 + 55% alpha
 * - Distant lines: smaller still, color = ink4 + 35% alpha
 *
 * Romanization below each in Newsreader Italic Light, color = AccentDim.
 * Selection mode shows accent-ghost bg + left-gutter checkmark.
 * Per-word karaoke on active line when LRC+ words present.
 * Satu baris digambar oleh [LyricLineItem] di `LyricLine.kt`.
 *
 * `fontSize` user override multiplies LyricActive base (30sp). 17 default
 * maintains roughly current behavior; settings slider 12-56 maps directly.
 */
@Composable
fun LyricsList(
    /**
     * Bukan sekadar konten. Kosong karena LRCLIB bilang lagu ini tidak
     * berlirik itu beda dengan kosong karena jaringan mati, dan user berhak
     * tahu bedanya: yang satu permanen, yang satu tinggal ditunggu.
     */
    lyricsState: LyricsState,
    /**
     * Baris aktif, sudah dihitung di PlaybackController dengan offset user
     * ikut diperhitungkan. Komponen ini SENGAJA tidak menghitungnya sendiri:
     * salinan kedua dari logika itu dulu bikin layar dan notification bisa
     * menunjuk baris berbeda, dan cuma salinan milik UI yang menerapkan offset.
     */
    activeIndex: Int,
    /**
     * Posisi lirik (sudah termasuk offset), hanya untuk highlight per-kata di
     * LRC enhanced dan titik jeda. Bukan untuk menentukan baris aktif.
     */
    progressMs: Long,
    /** Key = [SyncedLine.id]. Bukan timeMs: timestamp bisa kembar. */
    romaji: Map<Int, String?> = emptyMap(),
    fontSize: Int = 30,
    lineSpacing: Int = 22,
    highContrast: Boolean = false,
    smoothScroll: Boolean = true,
    hapticEnabled: Boolean = true,
    onSeekToLine: ((SyncedLine) -> Unit)? = null,
    onLongPressShare: ((SyncedLine) -> Unit)? = null,
    selectedIds: Set<Int> = emptySet(),
    isSelecting: Boolean = false,
    modifier: Modifier = Modifier,
    /** Lagu sedang jalan. Karaoke hanya mengekstrapolasi posisi per frame selama ini true. */
    isPlaying: Boolean = true,
    /** Karaoke penuh per suku kata. Mati untuk head unit yang lemah. */
    lyricEffects: Boolean = true,
) {
    val sv = LocalSvColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val synced = (lyricsState as? LyricsState.Ready)?.result?.synced
        val plain = (lyricsState as? LyricsState.Ready)?.result?.plain
        when {
            lyricsState is LyricsState.Loading -> StatusText("Finding lyrics…")

            !synced.isNullOrEmpty() ->
                SyncedView(
                    content = SyncedContent(synced, romaji),
                    activeIndex = activeIndex,
                    clock = LyricsClock(progressMs, isPlaying),
                    prefs = LyricsDisplayPrefs(fontSize, lineSpacing, highContrast, smoothScroll, hapticEnabled, lyricEffects),
                    interaction = LineInteraction(onSeekToLine, onLongPressShare, selectedIds, isSelecting),
                )

            !plain.isNullOrBlank() -> PlainView(plain, fontSize, sv.ink2)

            // Offline dicoba ulang otomatis saat jaringan kembali
            // (PlaybackController), jadi pesannya tidak menyuruh replay.
            // Unavailable tetap: online tapi LRCLIB gagal tidak memicu
            // transisi jaringan apa pun, jadi replay memang jalan keluarnya.
            lyricsState is LyricsState.Offline ->
                StatusText("You're offline. Lyrics for this song aren't saved yet.")

            lyricsState is LyricsState.Unavailable ->
                StatusText("Couldn't load lyrics. Check your connection, then replay the song.")

            else -> StatusText("No lyrics found for this track")
        }
    }
}

@Composable
private fun StatusText(text: String) {
    val sv = LocalSvColors.current
    Text(
        text = text,
        style = SvType.BodyItalic,
        color = sv.ink3,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = SvSpace.s6),
    )
}

/** Baris synced plus romanisasinya. Key romaji = [SyncedLine.id]. */
@Immutable
private data class SyncedContent(
    val lines: List<SyncedLine>,
    val romaji: Map<Int, String?>,
) {
    /**
     * Romanisasi menambah teks kedua di bawah tiap baris, jadi barisnya lebih
     * tinggi dan jangkar yang dihitung untuk baris polos membuat baris aktif turun.
     */
    val hasRomanization: Boolean get() = romaji.values.any { !it.isNullOrBlank() }
}

/**
 * Pudarkan [fraction] teratas dan terbawah daftar ke transparan, supaya baris
 * yang masuk dan keluar layar tidak terpotong tajam oleh header atau transport.
 * Lapisan offscreen dibutuhkan supaya `DstIn` memotong isi daftar saja, bukan
 * latar di belakangnya.
 */
private fun Modifier.edgeFade(fraction: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fraction to Color.Black,
                1f - fraction to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Tinggi chrome di atas dan di bawah daftar lirik, dp. */
private data class ChromeInsets(val aboveDp: Float = 0f, val belowDp: Float = 0f)

/**
 * Ukur chrome di atas dan di bawah daftar lirik, bukan dititipkan pemanggil.
 * Nilainya beda di portrait, di panel landscape, dan di overlay, dan tiap
 * pemanggil yang menghitungnya sendiri berarti tiga tempat yang harus tetap
 * sepakat. Mengukur sendiri membuatnya benar di layout mana pun, termasuk yang
 * belum ada.
 */
private fun Modifier.measureChrome(density: Density, onMeasured: (ChromeInsets) -> Unit): Modifier =
    onGloballyPositioned { koordinat ->
        val akar = koordinat.findRootCoordinates()
        val atasPx = koordinat.positionInRoot().y
        val bawahPx = akar.size.height - atasPx - koordinat.size.height
        with(density) { onMeasured(ChromeInsets(atasPx.toDp().value, bawahPx.toDp().value)) }
    }

@Composable
private fun SyncedView(
    content: SyncedContent,
    activeIndex: Int,
    clock: LyricsClock,
    prefs: LyricsDisplayPrefs,
    interaction: LineInteraction,
) {
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val linePrefs = prefs.copy(
        distanceBlur = prefs.lyricEffects && !prefs.highContrast && !isDragged && !interaction.isSelecting,
    )
    val density = LocalDensity.current
    var chrome by remember { mutableStateOf(ChromeInsets()) }
    ResetScrollOnNewSong(content.lines, activeIndex, listState)
    LineChangeHaptic(activeIndex, prefs.hapticEnabled)

    // Diukur, bukan ditebak. Ruang atas/bawah dan jangkar baris aktif semuanya
    // turunan dari tinggi viewport nyata — lihat lyricsPadding().
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .measureChrome(density) { chrome = it },
    ) {
        val ukuran = lyricsPadding(
            viewportHeightDp = maxHeight.value,
            chromeAboveDp = chrome.aboveDp,
            chromeBelowDp = chrome.belowDp,
            lineHeightDp = lyricsTypicalLineDp(prefs.fontSize, content.hasRomanization),
        )
        // scrollOffset satuannya PIKSEL. Nilai 200 yang lama berarti jarak yang
        // berbeda di tiap kerapatan layar; sekarang dp dikonversi eksplisit.
        val jangkarPx = with(density) { ukuran.anchorDp.dp.roundToPx() }
        AutoScrollToActive(listState, activeIndex, jangkarPx, prefs.smoothScroll)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (prefs.lyricEffects) Modifier.edgeFade(EDGE_FADE_FRACTION) else Modifier),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = SvSpace.s3),
            verticalArrangement = Arrangement.spacedBy(prefs.lineSpacing.coerceIn(0, 40).dp),
        ) {
            item { IntroItem(content.lines, ukuran.leadingDp, activeIndex, clock.progressMs, prefs.fontSize) }
            lineItems(content, activeIndex, clock, linePrefs, interaction)
            item { Spacer(modifier = Modifier.height(ukuran.trailingDp.dp)) }
        }
    }
}

@Composable
private fun IntroItem(lines: List<SyncedLine>, leadingDp: Float, activeIndex: Int, progressMs: Long, fontSize: Int) {
    IntroSpacer(
        heightDp = leadingDp,
        introGapMs = introGapMs(lines),
        // Hanya selama intro: begitu baris pertama aktif, titiknya hilang.
        elapsedMs = progressMs.takeIf { activeIndex < 0 },
        color = LocalSvColors.current.ink3,
        fontSp = fontSize.toFloat(),
    )
}

private fun LazyListScope.lineItems(
    content: SyncedContent,
    activeIndex: Int,
    clock: LyricsClock,
    prefs: LyricsDisplayPrefs,
    interaction: LineInteraction,
) {
    // key = line.id, BUKAN line.timeMs. File LRC boleh punya beberapa baris
    // dengan timestamp identik, dan key duplikat membuat Compose melempar
    // IllegalArgumentException: Key was already used.
    itemsIndexed(content.lines, key = { _, line -> line.id }) { idx, line ->
        LyricLineItem(
            model = LyricLineModel(
                line = line,
                romaji = content.romaji[line.id],
                state = lyricState(idx, activeIndex),
                interludeGapMs = interludeGapMs(content.lines, idx),
                blurDp = lineBlurDp(idx, activeIndex, prefs.distanceBlur),
            ),
            prefs = prefs,
            interaction = interaction,
            clock = clock,
        )
    }
}

/**
 * Reset scroll ke atas saat KONTEN lirik berubah (ganti lagu). Tanpa ini,
 * kalau lirik lagu baru preloaded/cached, transisi null→loaded di controller
 * terjadi dalam 1 frame sehingga SyncedView tidak keluar-masuk composition dan
 * LazyListState lama (ter-scroll di bawah dari lagu sebelumnya) kebawa. Hanya
 * reset kalau belum ada active line (intro) — kalau sudah ada, biarkan
 * [AutoScrollToActive] yang handle supaya tidak double-jump.
 */
@Composable
private fun ResetScrollOnNewSong(lines: List<SyncedLine>, activeIndex: Int, listState: LazyListState) {
    LaunchedEffect(lines) {
        if (activeIndex < 0) listState.scrollToItem(0)
    }
}

/** Smooth scroll uses spring. Snap mode uses scrollToItem (no anim). */
@Composable
private fun AutoScrollToActive(
    listState: LazyListState,
    activeIndex: Int,
    jangkarPx: Int,
    smoothScroll: Boolean,
) {
    LaunchedEffect(activeIndex, jangkarPx) {
        if (activeIndex < 0) return@LaunchedEffect
        // lyricsScrollIndex, BUKAN activeIndex mentah. Spacer atas memakai
        // indeks 0 LazyColumn, jadi lines[i] ada di indeks i+1.
        val target = lyricsScrollIndex(activeIndex)
        if (smoothScroll) {
            listState.animateScrollToItem(index = target, scrollOffset = -jangkarPx)
        } else {
            listState.scrollToItem(index = target, scrollOffset = -jangkarPx)
        }
    }
}

@Composable
private fun LineChangeHaptic(activeIndex: Int, enabled: Boolean) {
    if (!enabled) return
    val context = LocalContext.current
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) Haptics.tick(context)
    }
}

@Composable
private fun PlainView(plain: String, fontSize: Int, color: Color) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SvSpace.s5, vertical = SvSpace.s3),
    ) {
        item {
            Text(
                text = plain,
                style = SvType.LyricPassive.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp),
                color = color,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// findActiveIndex dulu ada di sini sebagai salinan kedua dari yang di
// PlaybackController. Sekarang satu-satunya implementasi ada di
// domain/LyricsSync.kt dan hasilnya masuk lewat parameter activeIndex.
