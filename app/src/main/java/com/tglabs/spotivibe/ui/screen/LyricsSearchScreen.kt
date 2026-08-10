package com.tglabs.spotivibe.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.LyricsSearchResult
import com.tglabs.spotivibe.domain.LyricsSearchState
import com.tglabs.spotivibe.domain.formatDuration
import com.tglabs.spotivibe.ui.component.HairlineRule
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Pencarian lirik manual, untuk saat auto-match memuat lirik dari lagu yang
 * sama sekali berbeda.
 *
 * Composable ini sengaja tipis: penyaringan hasil, format durasi, dan penanda
 * ter-sync semuanya sudah diputuskan di `domain/LyricsSearch.kt` yang teruji.
 * Yang tersisa di sini cuma penggambaran.
 */
@Composable
fun LyricsSearchScreen(
    initialTitle: String,
    initialArtist: String,
    state: LyricsSearchState,
    hasRememberedOverride: Boolean,
    onSearch: (title: String, artist: String) -> Unit,
    onPick: (result: LyricsSearchResult, remember: Boolean) -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    val sv = LocalSvColors.current
    BackHandler(onBack = onBack)

    // Terisi dari Spotify tapi bisa diedit: justru ketidakcocokan judul yang
    // bikin auto-match meleset, jadi user harus bisa memperbaikinya.
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var artist by rememberSaveable(initialArtist) { mutableStateOf(initialArtist) }

    // Sengaja TIDAK lengket antar pembukaan layar. Menyimpan permanen harus
    // selalu tindakan sadar, tidak pernah terjadi karena lupa mematikannya dari
    // pencarian sebelumnya.
    var remember by rememberSaveable { mutableStateOf(false) }

    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sv.bg0)
            .padding(horizontal = SvSpace.s5),
    ) {
        Spacer(modifier = Modifier.height(SvSpace.s8))
        SearchHeader(onBack = onBack)
        Spacer(modifier = Modifier.height(SvSpace.s3))
        HairlineRule(soft = true)

        if (isLandscape) {
            // Rasio 1 : 1.4 sama persis dengan layar lirik landscape, supaya
            // kedua layar terasa satu sistem dan mata tidak perlu menyesuaikan
            // lebar kolom saat berpindah.
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = SvSpace.s5, top = SvSpace.s4),
                ) {
                    SearchInputs(
                        title = title,
                        artist = artist,
                        onTitleChange = { title = it },
                        onArtistChange = { artist = it },
                        onSearch = { onSearch(title.trim(), artist.trim()) },
                        remember = remember,
                        onToggleRemember = { remember = !remember },
                        hasRememberedOverride = hasRememberedOverride,
                        onForget = onForget,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(sv.rule),
                )
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                        .padding(start = SvSpace.s5),
                ) {
                    SearchResults(state = state, onPick = { onPick(it, remember) })
                }
            }
        } else {
            Spacer(modifier = Modifier.height(SvSpace.s4))
            SearchInputs(
                title = title,
                artist = artist,
                onTitleChange = { title = it },
                onArtistChange = { artist = it },
                onSearch = { onSearch(title.trim(), artist.trim()) },
                remember = remember,
                onToggleRemember = { remember = !remember },
                hasRememberedOverride = hasRememberedOverride,
                onForget = onForget,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SearchResults(state = state, onPick = { onPick(it, remember) })
            }
        }
    }
}

@Composable
private fun SearchHeader(onBack: () -> Unit) {
    val sv = LocalSvColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoEyebrow(text = "SEARCH LYRICS")
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(SvRadius.r2))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SvIcons.Close,
                contentDescription = "Close",
                tint = sv.ink2,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SearchInputs(
    title: String,
    artist: String,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onSearch: () -> Unit,
    remember: Boolean,
    onToggleRemember: () -> Unit,
    hasRememberedOverride: Boolean,
    onForget: () -> Unit,
) {
    val sv = LocalSvColors.current
    FieldRow(label = "TITLE", value = title, onValueChange = onTitleChange)
    Spacer(modifier = Modifier.height(SvSpace.s3))
    FieldRow(label = "ARTIST", value = artist, onValueChange = onArtistChange)

    Spacer(modifier = Modifier.height(SvSpace.s4))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        com.tglabs.spotivibe.ui.component.AccentCtaButton(label = "SEARCH", onClick = onSearch)
    }

    Spacer(modifier = Modifier.height(SvSpace.s4))
    HairlineRule(soft = true)
    CheckRow(
        checked = remember,
        label = "Remember these lyrics for this song",
        onToggle = onToggleRemember,
    )
    HairlineRule(soft = true)

    if (hasRememberedOverride) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onForget)
                .padding(vertical = SvSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Forget saved lyrics for this song",
                style = SvType.BodyItalic.copy(fontSize = 14.sp),
                color = sv.accent,
            )
        }
        HairlineRule(soft = true)
    }
}

@Composable
private fun SearchResults(
    state: LyricsSearchState,
    onPick: (LyricsSearchResult) -> Unit,
) {
    val sv = LocalSvColors.current
    when (state) {
        LyricsSearchState.Idle -> Hint(
            "Type a title or artist, then hit Search. " +
                "The fields are prefilled from Spotify, but you can edit them."
        )
        LyricsSearchState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = sv.accent, strokeWidth = 2.dp)
        }
        LyricsSearchState.Empty -> Hint("No results. Try a different title or artist.")
        is LyricsSearchState.Failed -> Hint(
            "Search failed (${state.reason}). Check your connection and try again."
        )
        is LyricsSearchState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.results, key = { it.lrclibId }) { hasil ->
                ResultRow(hasil) { onPick(hasil) }
                HairlineRule(soft = true)
            }
            item { Spacer(modifier = Modifier.height(SvSpace.s8)) }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, onValueChange: (String) -> Unit) {
    val sv = LocalSvColors.current
    Column {
        MonoEyebrow(text = label, color = sv.ink3)
        Spacer(modifier = Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = SvType.Body.copy(color = sv.ink1, fontSize = 16.sp),
            cursorBrush = SolidColor(sv.accent),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r2))
                .padding(horizontal = SvSpace.s3, vertical = SvSpace.s3),
        )
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    val sv = LocalSvColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = SvSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.dp, if (checked) sv.accent else sv.rule, RoundedCornerShape(3.dp))
                .background(
                    if (checked) sv.accent else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(3.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = SvIcons.Check,
                    contentDescription = null,
                    tint = sv.accentInk,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(text = label, style = SvType.Body.copy(fontSize = 14.sp), color = sv.ink1)
    }
}

@Composable
private fun ResultRow(hasil: LyricsSearchResult, onClick: () -> Unit) {
    val sv = LocalSvColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = SvSpace.s3),
    ) {
        Text(
            text = hasil.trackName,
            style = SvType.Body.copy(fontSize = 16.sp),
            color = sv.ink1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = listOf(hasil.artistName, hasil.albumName)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = SvType.BodyItalic.copy(fontSize = 13.sp),
            color = sv.ink3,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Durasi paling menentukan untuk mengenali versi live atau extended.
        MonoEyebrow(
            text = "${formatDuration(hasil.durationSec)} · " +
                if (hasil.hasSynced) "SYNCED" else "PLAIN TEXT",
            color = if (hasil.hasSynced) sv.accent else sv.ink3,
        )
    }
}

@Composable
private fun Hint(text: String) {
    val sv = LocalSvColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(top = SvSpace.s8),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = SvType.BodyItalic.copy(fontSize = 14.sp),
            color = sv.ink3,
            textAlign = TextAlign.Center,
        )
    }
}
