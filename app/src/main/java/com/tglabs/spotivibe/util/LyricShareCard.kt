package com.tglabs.spotivibe.util

import android.content.ClipData
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
 * Render 1-5 baris lirik ke bitmap 1080×1080 + share via system chooser.
 * Tidak pakai Compose-to-bitmap karena lebih reliable pakai Canvas langsung
 * (no recomposition hazard, no measuring twice). Format square biar oke
 * di Twitter / IG / Threads.
 */
object LyricShareCard {

    /** Satu baris di card — text utama + romaji opsional. */
    data class Entry(val text: String, val romaji: String?)

    /**
     * Share 1-5 baris lirik. Layout adaptif berdasarkan count:
     * - 1 baris → font besar + decorative quote mark
     * - 2-5 baris → font scaled-down, tanpa quote, gap antar baris konsisten
     */
    fun shareLines(
        context: Context,
        entries: List<Entry>,
        title: String,
        artist: String,
        accentArgb: Int,
    ) {
        require(entries.isNotEmpty()) { "entries must not be empty" }
        require(entries.size <= 5) { "max 5 lines per card" }

        val bitmap = render(entries, title, artist, accentArgb)
        val uri = persist(context, bitmap)
        val caption = buildCaption(entries, title, artist)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            // ClipData WAJIB diset eksplisit supaya Android Sharesheet render
            // rich preview (thumbnail) image — kalau cuma EXTRA_STREAM, banyak
            // launcher (terutama Android 12+) cuma tampilin text preview saja.
            clipData = ClipData.newUri(context.contentResolver, "Lyric card", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share lyric").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun buildCaption(entries: List<Entry>, title: String, artist: String): String {
        return if (entries.size == 1) {
            "\"${entries[0].text}\" — $title · $artist"
        } else {
            val body = entries.joinToString("\n") { it.text }
            "$body\n\n— $title · $artist"
        }
    }

    private fun render(
        entries: List<Entry>,
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

        // Sizing yang adaptif. Single-line dapat decorative quote mark.
        // Multi-line skip quote — content harus muat dalam ~720px tinggi area.
        val isSingle = entries.size == 1
        val (lineSize, romaSize, lineGap) = sizingFor(entries.size)

        if (isSingle) {
            // Decorative ❝ — only for single-line; multiline already busy enough.
            val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (accentArgb and 0x00FFFFFF) or 0x55000000.toInt()
                textSize = 320f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("“", 60f, 320f, quotePaint)
        }

        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = lineSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val romaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = romaSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }

        // Pre-wrap supaya bisa hitung total tinggi → vertikal center di tengah area.
        val maxTextWidth = size - 160
        data class RenderedBlock(val lines: List<String>, val romajiLines: List<String>)
        val blocks = entries.map { e ->
            RenderedBlock(
                lines = wrap(e.text.ifBlank { "♪" }, linePaint, maxTextWidth),
                romajiLines = if (!e.romaji.isNullOrBlank()) wrap(e.romaji, romaPaint, maxTextWidth) else emptyList(),
            )
        }

        val lineStep = lineSize + 16f
        val romaStep = romaSize + 8f
        val totalHeight = blocks.sumOf { b ->
            (b.lines.size * lineStep + b.romajiLines.size * romaStep + (if (b.romajiLines.isNotEmpty()) 12f else 0f)).toDouble()
        }.toFloat() + (blocks.size - 1) * lineGap

        // Vertical center within the content band (top 200px header area, bottom 200px footer area)
        val contentTop = 220f
        val contentBottom = (size - 200).toFloat()
        val contentArea = contentBottom - contentTop
        var y = contentTop + (contentArea - totalHeight) / 2f + lineSize

        blocks.forEach { block ->
            block.lines.forEach { l ->
                canvas.drawText(l, 80f, y, linePaint)
                y += lineStep
            }
            if (block.romajiLines.isNotEmpty()) {
                y += 4f
                block.romajiLines.forEach { l ->
                    canvas.drawText(l, 80f, y, romaPaint)
                    y += romaStep
                }
            }
            y += lineGap
        }

        // Footer — track meta + brand
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFAAAAAA.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(truncate("$title · $artist", metaPaint, size - 160), 80f, (size - 120).toFloat(), metaPaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Spotivibe", 80f, (size - 70).toFloat(), brandPaint)

        return bmp
    }

    /** Triple of (lineFontSize, romajiFontSize, gapBetweenBlocks). Scaled down per count. */
    private fun sizingFor(count: Int): Triple<Float, Float, Float> = when (count) {
        1 -> Triple(64f, 42f, 0f)
        2 -> Triple(58f, 38f, 24f)
        3 -> Triple(52f, 34f, 20f)
        4 -> Triple(46f, 30f, 16f)
        else -> Triple(40f, 28f, 12f) // 5
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
