package dev.arisu.shrine.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
import dev.arisu.shrine.core.util.markdownPlainText
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShareCardData(
    val kind: String,
    val meta: String,
    val id: String,
    val title: String,
    val body: String,
    val author: String,
    val time: String,
)

object ShareCard {

    private const val WIDTH = 1080
    private const val PAD = 72
    private const val MAX_HEIGHT = 2400
    private const val MIN_HEIGHT = 960
    private const val CONTENT_W = WIDTH - PAD * 2

    private val paper = 0xFFF2EFEB.toInt()
    private val ink = 0xFF1A1512.toInt()
    private val bodyInk = 0xFF2E2723.toInt()
    private val muted = 0xFF6E655D.toInt()
    private val vermilion = 0xFFB02A21.toInt()

    suspend fun share(context: Context, data: ShareCardData) {
        val bitmap = withContext(Dispatchers.Default) { render(context, data) }
        val file = withContext(Dispatchers.IO) { save(context, bitmap, data.id) }
        bitmap.recycle()
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享卡片"))
        }
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

        var bodyLines = 10
        var bodyLayout = layout(data.body, bodyPaint, CONTENT_W, bodyLines)
        while (bodyLines > 2) {
            val height = cardHeight(titleLayout.height, bodyLayout.height)
            if (height <= MAX_HEIGHT) break
            bodyLines -= 1
            bodyLayout = layout(data.body, bodyPaint, CONTENT_W, bodyLines)
        }
        val height = cardHeight(titleLayout.height, bodyLayout.height)
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

        drawFooter(canvas, data, height, serif, serifSemi)
        return bitmap
    }

    private fun cardHeight(titleHeight: Int, bodyHeight: Int): Int =
        232 + titleHeight + 34 + 1 + 34 + bodyHeight + 56 + 96 + PAD

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

    fun plainText(markdown: String): String = markdownPlainText(markdown)
}
