package com.tglabs.spotivibe.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.tglabs.spotivibe.domain.truncateText
import com.tglabs.spotivibe.domain.wrapText
import java.io.File
import java.io.FileOutputStream

/**
 * Render 1-5 baris lirik ke bitmap 1080×1920 + share via system chooser.
 * Editorial layout per claude-design-handoff: mono "EXCERPT" eyebrow +
 * hairline divider + big Newsreader lyrics with AccentDim italic
 * romanization underneath each + mono colophon footer.
 *
 * Canvas API langsung (no Compose-to-bitmap) untuk reliability — no
 * recomposition hazard, predictable layout.
 *
 * Format: 1080×1920 (9:16 portrait, IG / Threads / WA story-friendly).
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
        val uri = try {
            persist(context, bitmap)
        } finally {
            // Bitmap 1080x1920 ARGB_8888 = 8,3 MB. Isinya sudah masuk file, jadi
            // memorinya tidak ada gunanya lagi. GC memang akhirnya membereskan,
            // tapi share beberapa kali beruntun menumpuk tekanan memori yang
            // tidak perlu.
            bitmap.recycle()
        }
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
        // Editorial portrait format — 9:16. Width 1080, height 1920.
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // BG: paper-warm dark gradient. Top sedikit lighter, bottom accent-tinted.
        val inkBgTop = 0xFF1A1B22.toInt()     // BgDark1
        val inkBgBottom = blendColor(accentArgb, 0xFF14151B.toInt(), 0.25f)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(inkBgTop, inkBgBottom),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Layout constants — magazine grid
        val margin = 100f
        val maxTextWidth = (w - 2 * margin).toInt()

        // ── Top: eyebrow + hairline rule ────────────────────
        val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A877F.toInt() // InkDark3
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText("EXCERPT · ${entries.size} OF 5", margin, 180f, eyebrowPaint)

        // Hairline rule under eyebrow
        val rulePaint = Paint().apply {
            color = 0xFF3C3D44.toInt() // RuleDark
            strokeWidth = 1f
        }
        canvas.drawLine(margin, 220f, (w - margin).toFloat(), 220f, rulePaint)

        // ── Center: lyric block ──────────────────────────────
        val (lineSize, romaSize, lineGap) = sizingFor(entries.size)
        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF4F1EA.toInt() // InkDark1
            textSize = lineSize
            // Newsreader serif tidak tersedia di Canvas; SERIF system fallback
            // memberi feel yang dekat untuk runtime render.
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            letterSpacing = -0.005f
        }
        val romaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dimAccent(accentArgb)
            textSize = romaSize
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        data class RenderedBlock(val lines: List<String>, val romajiLines: List<String>)
        val blocks = entries.map { e ->
            RenderedBlock(
                lines = wrap(e.text.ifBlank { "♪" }, linePaint, maxTextWidth),
                romajiLines = if (!e.romaji.isNullOrBlank()) wrap(e.romaji, romaPaint, maxTextWidth) else emptyList(),
            )
        }

        val lineStep = lineSize + 16f
        val romaStep = romaSize + 6f
        val totalHeight = blocks.sumOf { b ->
            (b.lines.size * lineStep + b.romajiLines.size * romaStep + (if (b.romajiLines.isNotEmpty()) 14f else 0f)).toDouble()
        }.toFloat() + (blocks.size - 1) * lineGap

        val contentTop = 320f
        val contentBottom = (h - 280).toFloat()
        val contentArea = contentBottom - contentTop
        var y = contentTop + (contentArea - totalHeight) / 2f + lineSize

        blocks.forEach { block ->
            block.lines.forEach { l ->
                canvas.drawText(l, margin, y, linePaint)
                y += lineStep
            }
            if (block.romajiLines.isNotEmpty()) {
                y += 6f
                block.romajiLines.forEach { l ->
                    canvas.drawText(l, margin, y, romaPaint)
                    y += romaStep
                }
            }
            y += lineGap
        }

        // ── Bottom: hairline rule + track meta + brand ───────
        canvas.drawLine(margin, (h - 240).toFloat(), (w - margin).toFloat(), (h - 240).toFloat(), rulePaint)

        // Track meta — serif title, mono artist
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC2BFB6.toInt() // InkDark2
            textSize = 42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            letterSpacing = -0.01f
        }
        canvas.drawText(truncate(title, titlePaint, maxTextWidth), margin, (h - 170).toFloat(), titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A877F.toInt() // InkDark3
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(truncate(artist, artistPaint, maxTextWidth), margin, (h - 130).toFloat(), artistPaint)

        // Brand colophon — bottom right mono
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        canvas.drawText("SPOTIVIBE", margin, (h - 70).toFloat(), brandPaint)

        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A877F.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.15f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("CODE · VIBE · SING", (w - margin).toFloat(), (h - 70).toFloat(), tagPaint)

        return bmp
    }

    /** Compute accent-dim untuk romanization. Simple: factor down RGB
     *  components ke 50% supaya kelihatan "same hue, lower lightness". */
    private fun dimAccent(accentArgb: Int): Int {
        val r = (Color.red(accentArgb) * 0.62f).toInt()
        val g = (Color.green(accentArgb) * 0.62f).toInt()
        val b = (Color.blue(accentArgb) * 0.62f).toInt()
        return Color.argb(255, r, g, b)
    }

    /** Triple of (lineFontSize, romajiFontSize, gapBetweenBlocks). Scaled
     *  per count untuk 1080×1920 layout — sedikit lebih besar dari versi
     *  square karena ada lebih banyak vertical real estate. */
    private fun sizingFor(count: Int): Triple<Float, Float, Float> = when (count) {
        1 -> Triple(82f, 50f, 0f)
        2 -> Triple(72f, 44f, 32f)
        3 -> Triple(64f, 40f, 28f)
        4 -> Triple(56f, 36f, 22f)
        else -> Triple(48f, 32f, 18f) // 5
    }

    /**
     * Word-wrap sadar CJK. Logikanya ada di [wrapText] supaya bisa diuji tanpa
     * Android; di sini cuma menyuntikkan pengukurnya.
     *
     * `measureText` (advance width), BUKAN `getTextBounds` (ink bounds). Yang
     * kedua mengembalikan kotak ketat di sekitar goresan glyph dan mengabaikan
     * side bearing serta spasi tepi, jadi keputusan layoutnya meleset.
     */
    private fun wrap(text: String, paint: Paint, maxWidth: Int): List<String> =
        wrapText(text, maxWidth.toFloat()) { paint.measureText(it) }

    private fun truncate(text: String, paint: Paint, maxWidth: Int): String =
        truncateText(text, maxWidth.toFloat()) { paint.measureText(it) }

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
        sweepOldCards(dir)
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

    /**
     * Buang card lama. Tanpa ini setiap share meninggalkan PNG yang tidak
     * pernah dihapus; lokasinya memang `cacheDir` sehingga sistem BOLEH
     * menghapusnya, tapi baru saat penyimpanan sudah sesak. Sebelum itu
     * ratusan file menumpuk.
     *
     * Ambang [CARD_TTL_MS] sengaja longgar. App tujuan share membaca file lewat
     * FileProvider tak lama setelah intent dikirim, tapi menghapus terlalu
     * cepat berisiko menarik file dari bawah kaki app yang lambat membuka.
     */
    private fun sweepOldCards(dir: File) {
        runCatching {
            val batas = System.currentTimeMillis() - CARD_TTL_MS
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < batas) f.delete()
            }
        }.onFailure { Log.w(TAG, "Gagal menyapu card lama: ${it.message}") }
    }

    private const val TAG = "LyricShareCard"

    /** Satu jam. Jauh lebih lama dari yang dibutuhkan, dan tetap terbatas. */
    private const val CARD_TTL_MS = 60L * 60L * 1000L
}
