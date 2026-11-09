package dev.arisu.shrine.feature.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import dev.arisu.shrine.R
import dev.arisu.shrine.core.media.imageSampleSize
import dev.arisu.shrine.core.util.markdownPlainText
import dev.arisu.shrine.data.remote.Api
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ShareCardData(
    val kind: String,
    val meta: String,
    val id: String,
    val title: String,
    val body: String,
    val author: String,
    val time: String,

    val images: List<String> = emptyList(),
)

object ShareCard {

    private const val WIDTH = 1080
    private const val PAD = 72
    private const val MAX_HEIGHT = 2400
    private const val MIN_HEIGHT = 960
    private const val CONTENT_W = WIDTH - PAD * 2

    private const val MAX_IMAGES = 4
    private const val IMAGE_GAP = 14
    private const val IMAGE_GAP_ABOVE = 42
    private const val IMAGE_RADIUS = 18f

    private val paper = 0xFFF2EFEB.toInt()
    private val ink = 0xFF1A1512.toInt()
    private val bodyInk = 0xFF2E2723.toInt()
    private val muted = 0xFF6E655D.toInt()
    private val vermilion = 0xFFB02A21.toInt()
    private val imagePlaceholder = 0xFFE8E4DE.toInt()

    suspend fun share(context: Context, data: ShareCardData) {
        val bitmap = withContext(Dispatchers.Default) { render(context, data) }
        try {
            shareBitmap(context, bitmap, data.id)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        id: String,
        chooserTitle: String = "分享卡片",
    ) {
        val file = withContext(Dispatchers.IO) { save(context, bitmap, id) }
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooserWithPreviewGrant(intent, chooserTitle))
        }
    }

    internal fun chooserWithPreviewGrant(intent: Intent, chooserTitle: String): Intent =
        Intent.createChooser(intent, chooserTitle).apply {
            clipData = intent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    internal fun render(context: Context, data: ShareCardData): Bitmap {
        val serif = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_400), Typeface.NORMAL)
        val serifSemi = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_600), Typeface.NORMAL)
        val serifBold = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_700), Typeface.NORMAL)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 46f
            typeface = serifBold
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bodyInk
            textSize = 32f
            typeface = serif
        }
        val titleLayout = layout(data.title.ifBlank { "无题" }, titlePaint, CONTENT_W, 3)

        val imageUrls = data.images.distinct().take(MAX_IMAGES)
        val imageBitmaps = imageUrls.map { loadBitmap(it) }
        val imageHeight = imagePlanHeight(imageUrls.size)

        try {

            var bodyLines = 10
            var bodyLayout = layout(data.body, bodyPaint, CONTENT_W, bodyLines)
            while (bodyLines > 2) {
                val height = cardHeight(titleLayout.height, bodyLayout.height, imageHeight)
                if (height <= MAX_HEIGHT) break
                bodyLines -= 1
                bodyLayout = layout(data.body, bodyPaint, CONTENT_W, bodyLines)
            }
            val height = cardHeight(titleLayout.height, bodyLayout.height, imageHeight)
                .coerceIn(MIN_HEIGHT, MAX_HEIGHT)

            val bitmap = createBitmap(WIDTH, height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(paper)

            val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, 420f,
                    Color.argb(30, 176, 42, 33), Color.argb(0, 176, 42, 33),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), 420f, wash)

            drawHeader(canvas, data, serif, serifSemi, serifBold)
            var y = 232f
            canvas.withTranslation(PAD.toFloat(), y) { titleLayout.draw(this) }
            y += titleLayout.height + 34f

            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 176, 42, 33) }
            canvas.drawRect(PAD.toFloat(), y, (WIDTH - PAD).toFloat(), y + 1.5f, line)
            y += 34f

            canvas.withTranslation(PAD.toFloat(), y) { bodyLayout.draw(this) }
            y += bodyLayout.height

            if (imageUrls.isNotEmpty()) {
                y += IMAGE_GAP_ABOVE
                drawImages(
                    canvas = canvas,
                    bitmaps = imageBitmaps,
                    total = data.images.size,
                    top = y,
                    serif = serif,
                )
            }

            drawFooter(canvas, data, height, serif, serifSemi)
            return bitmap
        } finally {
            imageBitmaps.forEach { it?.recycle() }
        }
    }

    private fun cardHeight(titleHeight: Int, bodyHeight: Int, imageHeight: Int): Int =
        232 + titleHeight + 34 + 1 + 34 + bodyHeight +
            (if (imageHeight > 0) IMAGE_GAP_ABOVE + imageHeight else 0) +
            56 + 96 + PAD

    private fun imagePlanHeight(count: Int): Int = when (count) {
        0 -> 0
        1 -> 500
        2 -> 300
        3 -> 300 + IMAGE_GAP + 400
        4 -> 300 + IMAGE_GAP + 300
        else -> 0
    }

    private fun drawImages(
        canvas: Canvas,
        bitmaps: List<Bitmap?>,
        total: Int,
        top: Float,
        serif: Typeface,
    ) {
        val half = (CONTENT_W - IMAGE_GAP) / 2f
        val cells = ArrayList<RectF>(bitmaps.size)
        when (bitmaps.size) {
            1 -> cells += RectF(
                PAD.toFloat(), top,
                (PAD + CONTENT_W).toFloat(), top + 500f,
            )
            2 -> {
                cells += RectF(PAD.toFloat(), top, PAD + half, top + 300f)
                cells += RectF(PAD + half + IMAGE_GAP, top, (PAD + CONTENT_W).toFloat(), top + 300f)
            }
            3 -> {
                cells += RectF(PAD.toFloat(), top, PAD + half, top + 300f)
                cells += RectF(PAD + half + IMAGE_GAP, top, (PAD + CONTENT_W).toFloat(), top + 300f)
                cells += RectF(
                    PAD.toFloat(), top + 300f + IMAGE_GAP,
                    (PAD + CONTENT_W).toFloat(), top + 300f + IMAGE_GAP + 400f,
                )
            }
            4 -> {
                cells += RectF(PAD.toFloat(), top, PAD + half, top + 300f)
                cells += RectF(PAD + half + IMAGE_GAP, top, (PAD + CONTENT_W).toFloat(), top + 300f)
                cells += RectF(
                    PAD.toFloat(), top + 300f + IMAGE_GAP,
                    PAD + half, top + 600f + IMAGE_GAP,
                )
                cells += RectF(
                    PAD + half + IMAGE_GAP, top + 300f + IMAGE_GAP,
                    (PAD + CONTENT_W).toFloat(), top + 600f + IMAGE_GAP,
                )
            }
        }
        cells.forEachIndexed { index, rect ->
            drawImage(canvas, bitmaps.getOrNull(index), rect, serif)
        }
        if (total > MAX_IMAGES && cells.isNotEmpty()) {
            val last = cells.last()
            val label = "+${total - MAX_IMAGES}"
            val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 26f
                typeface = serif
                textAlign = Paint.Align.CENTER
            }
            val textWidth = text.measureText(label)
            val badge = RectF(
                last.right - textWidth - 30f,
                last.bottom - 50f,
                last.right - 12f,
                last.bottom - 12f,
            )
            canvas.drawRoundRect(badge, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 18, 14, 11)
            })
            val fm = text.fontMetrics
            canvas.drawText(label, badge.centerX(), badge.centerY() - (fm.ascent + fm.descent) / 2f, text)
        }
    }

    private fun drawImage(canvas: Canvas, bitmap: Bitmap?, rect: RectF, serif: Typeface) {
        val path = Path().apply {
            addRoundRect(rect, IMAGE_RADIUS, IMAGE_RADIUS, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        if (bitmap == null) {
            canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = imagePlaceholder })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muted
                textSize = 28f
                typeface = serif
                textAlign = Paint.Align.CENTER
            }
            val fm = text.fontMetrics
            canvas.drawText(
                "图片",
                rect.centerX(),
                rect.centerY() - (fm.ascent + fm.descent) / 2f,
                text,
            )
        } else {
            val src = centerCrop(bitmap.width, bitmap.height, rect.width(), rect.height())
            canvas.drawBitmap(
                bitmap,
                src,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        canvas.restore()

        canvas.drawRoundRect(
            rect,
            IMAGE_RADIUS,
            IMAGE_RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = Color.argb(26, 26, 21, 18)
            },
        )
    }

    private fun centerCrop(width: Int, height: Int, targetWidth: Float, targetHeight: Float): Rect {
        if (width <= 0 || height <= 0) return Rect(0, 0, width, height)
        val targetRatio = targetWidth / targetHeight
        val sourceRatio = width.toFloat() / height
        return if (sourceRatio > targetRatio) {
            val cropW = (height * targetRatio).toInt().coerceIn(1, width)
            val left = (width - cropW) / 2
            Rect(left, 0, left + cropW, height)
        } else {
            val cropH = (width / targetRatio).toInt().coerceIn(1, height)
            val top = (height - cropH) / 2
            Rect(0, top, width, top + cropH)
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.55f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun drawHeader(
        canvas: Canvas,
        data: ShareCardData,
        serif: Typeface,
        serifSemi: Typeface,
        serifBold: Typeface,
    ) {

        val seal = RectF(PAD.toFloat(), 74f, PAD + 64f, 138f)
        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vermilion }
        canvas.drawRoundRect(seal, 12f, 12f, sealPaint)
        val sealText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = serifBold
            textAlign = Paint.Align.CENTER
        }
        val fm = sealText.fontMetrics
        canvas.drawText("有", seal.centerX(), seal.centerY() - (fm.ascent + fm.descent) / 2f, sealText)

        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 30f
            typeface = serifSemi
        }
        canvas.drawText("有栖神社", PAD + 84f, 112f, brand)

        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 23f
            typeface = serif
        }
        canvas.drawText("${data.kind} · ${data.meta}", PAD + 84f, 144f, meta)

        val id = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 23f
            typeface = serif
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(data.id, (WIDTH - PAD).toFloat(), 112f, id)
    }

    private fun drawFooter(
        canvas: Canvas,
        data: ShareCardData,
        height: Int,
        serif: Typeface,
        serifSemi: Typeface,
    ) {
        val footerY = height - PAD - 40f
        val author = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bodyInk
            textSize = 26f
            typeface = serifSemi
        }
        canvas.drawText(data.author, PAD.toFloat(), footerY, author)
        val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 23f
            typeface = serif
        }
        canvas.drawText(data.time, PAD.toFloat(), footerY + 34f, time)

        val seal = RectF((WIDTH - PAD - 84).toFloat(), (height - PAD - 84).toFloat(), (WIDTH - PAD).toFloat(), (height - PAD).toFloat())
        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vermilion }
        canvas.drawRoundRect(seal, 14f, 14f, sealPaint)
        val sealText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = serifSemi
            textAlign = Paint.Align.CENTER
        }
        val fm = sealText.fontMetrics
        canvas.drawText("有栖", seal.centerX(), seal.centerY() - (fm.ascent + fm.descent) / 2f, sealText)
    }

    private fun save(context: Context, bitmap: Bitmap, id: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "share_$safe.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun plainText(markdown: String): String = markdownPlainText(markdown, keepImages = false)

    fun images(markdown: String): List<String> {
        val cleaned = markdown
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("`[^`]*`"), "")
        val out = LinkedHashSet<String>()
        Regex("""!\[[^\]]*]\(([^)\s]+)\)""").findAll(cleaned).forEach { out += it.groupValues[1] }
        Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(cleaned)
            .forEach { out += it.groupValues[1] }
        return out.toList()
    }

    private val imageClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun loadBitmap(rawUrl: String): Bitmap? {
        return try {
            val url = when {
                rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                rawUrl.startsWith("/") -> Api.currentBaseUrl + rawUrl
                else -> Api.currentBaseUrl + "/" + rawUrl
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) return null
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ArisuShrine-Share/1.0")
                .header("Accept", "image/*")
                .build()
            imageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("ShareCard", "image HTTP ${response.code}: $url")
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size > 20 * 1024 * 1024) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    android.util.Log.w("ShareCard", "image bounds 0: $url (${bytes.size}B)")
                    return null
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, 1200)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }
        } catch (e: Exception) {
            android.util.Log.w("ShareCard", "image load failed: $rawUrl", e)
            null
        }
    }
}
