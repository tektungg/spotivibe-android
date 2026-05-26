package com.tglabs.spotivibe.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Render baris lirik ke bitmap 1080×1080 + share via system chooser.
 * Tidak pakai Compose-to-bitmap karena lebih reliable pakai Canvas langsung
 * (no recomposition hazard, no measuring twice). Format square biar oke
 * di Twitter / IG / Threads.
 */
object LyricShareCard {

    fun shareLine(
        context: Context,
        line: String,
        romaji: String?,
        title: String,
        artist: String,
        accentArgb: Int,
    ) {
        val bitmap = render(line, romaji, title, artist, accentArgb)
        val uri = persist(context, bitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "\"$line\" — $title · $artist")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share lyric").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun render(
        line: String,
        romaji: String?,
        title: String,
        artist: String,
        accentArgb: Int,
    ): Bitmap {
        val size = 1080
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // BG: vertical gradient — deep top, accent-tinted bottom.
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(0xFF0E0E12.toInt(), blendColor(accentArgb, 0xFF1A1A22.toInt(), 0.30f)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Accent quote mark — big "❝" decorative
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentArgb and 0x00FFFFFF) or 0x55000000.toInt() // 33% alpha
            textSize = 320f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", 60f, 320f, quotePaint)

        // Main lyric line — wrap manually.
        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val lines = wrap(line, linePaint, size - 160)
        var y = (size / 2f) - (lines.size * 80f) / 2f
        lines.forEach { l ->
            canvas.drawText(l, 80f, y, linePaint)
            y += 80f
        }

        // Romaji subtitle — italic, accent color
        if (!romaji.isNullOrBlank()) {
            val romaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentArgb
                textSize = 42f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            val romaLines = wrap(romaji, romaPaint, size - 160)
            y += 20f
            romaLines.forEach { l ->
                canvas.drawText(l, 80f, y, romaPaint)
                y += 56f
            }
        }

        // Footer — track meta + brand
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFAAAAAA.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(truncate("$title · $artist", metaPaint, size - 160), 80f, size - 120f, metaPaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Spotivibe", 80f, size - 70f, brandPaint)

        return bmp
    }

    /** Manual word-wrap. Tidak pakai StaticLayout supaya tetap ringan + predictable. */
    private fun wrap(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(' ')
        val result = mutableListOf<String>()
        val current = StringBuilder()
        val bounds = Rect()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            paint.getTextBounds(candidate, 0, candidate.length, bounds)
            if (bounds.width() > maxWidth && current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                current.clear()
                current.append(candidate)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Int): String {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() <= maxWidth) return text
        var cut = text.length
        while (cut > 1) {
            cut--
            val attempt = text.substring(0, cut) + "…"
            paint.getTextBounds(attempt, 0, attempt.length, bounds)
            if (bounds.width() <= maxWidth) return attempt
        }
        return text
    }

    /** Linear blend di sRGB space. Good enough untuk gradient subtle. */
    private fun blendColor(a: Int, b: Int, ratio: Float): Int {
        val r = (Color.red(a) * (1 - ratio) + Color.red(b) * ratio).toInt()
        val g = (Color.green(a) * (1 - ratio) + Color.green(b) * ratio).toInt()
        val bl = (Color.blue(a) * (1 - ratio) + Color.blue(b) * ratio).toInt()
        return Color.argb(255, r, g, bl)
    }

    private fun persist(context: Context, bmp: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "shared")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "lyric_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
