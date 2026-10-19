package com.tglabs.spotivibe.ui.screen

import com.tglabs.spotivibe.ui.theme.svSafeContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.ui.component.AccentCtaButton
import com.tglabs.spotivibe.ui.component.HairlineRule
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.theme.Brand
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import java.util.Calendar

/**
 * Layar pertama: satu izin sistem, bukan login.
 *
 * Spotivibe membaca lagu yang diputar app lain lewat MediaSession, dan Android
 * hanya mengizinkannya untuk app yang diberi "notification access". Layar ini
 * menjelaskan kenapa izin itu diminta dan membuka layar Settings-nya. Begitu
 * user kembali, `MainActivity.onResume` membaca ulang izinnya dan layar ini
 * hilang sendiri, tanpa tombol "lanjut".
 *
 * Tata letak mengikuti cover editorial lama (masthead, "Sing along.", CTA
 * penuh, colophon) supaya kesan pertama app tidak berubah.
 */
@Composable
fun AccessScreen(onGrantAccess: () -> Unit) {
    val sv = LocalSvColors.current
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sv.bg0)
            // Latar dulu, insets kemudian: lihat SvInsets.kt.
            .svSafeContent()
            .padding(horizontal = SvSpace.s5),
    ) {
        Masthead(year)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Sing\nalong.",
                style = SvType.Display.copy(fontSize = 84.sp, lineHeight = 80.sp, letterSpacing = (-2.5).sp),
                color = sv.ink1,
            )
            Spacer(modifier = Modifier.height(SvSpace.s2))
            Text(
                text = "Synced lyrics with romanization for whatever you play: Spotify, YouTube Music, or any music app.",
                style = SvType.BodyItalic.copy(fontSize = 18.sp, lineHeight = 24.sp),
                color = sv.ink3,
                modifier = Modifier.padding(end = SvSpace.s12),
            )
            Spacer(modifier = Modifier.height(SvSpace.s8))
            AccentCtaButton(
                label = "ALLOW NOTIFICATION ACCESS",
                onClick = onGrantAccess,
                trailingIcon = SvIcons.Next,
            )
            Spacer(modifier = Modifier.height(SvSpace.s3))
            Text(
                text = "Android calls reading what other apps are playing \"notification access\". " +
                    "Spotivibe only reads the song and its position. No account needed.",
                style = SvType.BodyItalic.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = sv.ink3,
            )
        }

        Colophon()
    }
}

@Composable
private fun Masthead(year: Int) {
    Spacer(modifier = Modifier.height(SvSpace.s3))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        MonoEyebrow(text = "VOL.01 · ISSUE 03")
        MonoEyebrow(text = "$year")
    }
    Spacer(modifier = Modifier.height(SvSpace.s3))
    HairlineRule(soft = true)
}

@Composable
private fun Colophon() {
    Spacer(modifier = Modifier.height(SvSpace.s4))
    HairlineRule(soft = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SvSpace.s3, horizontal = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoEyebrow(text = "SPOTIVIBE")
        MonoEyebrow(text = Brand.TAGLINE_EYEBROW)
    }
    Spacer(modifier = Modifier.height(SvSpace.s6))
}
