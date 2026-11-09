package dev.arisu.shrine.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import dev.arisu.shrine.R
import dev.arisu.shrine.core.media.imageSampleSize
import dev.arisu.shrine.core.media.saveBitmapToGallery
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.data.remote.Api
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class FortuneCardData(
    val luck: Int,
    val band: String,

    val date: String,
    val userName: String = "",
    val userNo: String = "",
    val avatarUrl: String? = null,
)

fun fortuneCardData(user: User?, luck: Int, band: String, date: String): FortuneCardData =
    FortuneCardData(
        luck = luck,
        band = band,
        date = date,
        userName = user?.nickname.orEmpty(),
        userNo = user?.id.orEmpty(),
        avatarUrl = user?.avatarUrl,
    )

object FortuneCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val PAD = 72

    private val lacquer = 0xFF9E1F18.toInt()
    private val cream = 0xFFF7EFE2.toInt()
    private val creamSoft = 0xCCF7EFE2.toInt()
    private val gold = 0xFFD8B36A.toInt()
    private val petal = 0x40F7EFE2

    suspend fun share(context: Context, data: FortuneCardData) {
        val bitmap = withContext(Dispatchers.Default) { render(context, data) }
        try {
            ShareCard.shareBitmap(
                context = context,
                bitmap = bitmap,
                id = fileId(data),
                chooserTitle = "分享御签",
            )
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun save(context: Context, data: FortuneCardData): Boolean {
        val bitmap = withContext(Dispatchers.Default) { render(context, data) }
        return try {
            saveBitmapToGallery(context, bitmap, fileId(data))
        } finally {
            bitmap.recycle()
        }
    }

    internal fun fileId(data: FortuneCardData): String =
        "omikuji_" + data.userNo.ifBlank { "anon" } + "_" + data.date.replace('.', '-')

    internal fun render(context: Context, data: FortuneCardData): Bitmap {
        val serif = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_400), Typeface.NORMAL)
        val serifSemi = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_600), Typeface.NORMAL)
        val serifBold = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_700), Typeface.NORMAL)

        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawColor(lacquer)

        val avatar = loadAvatar(data.avatarUrl)
        try {
            drawHeader(canvas, data, serif, serifSemi)
            drawHairline(canvas, 205f)

            canvas.drawRect(
                60f, 240f, 62f, 1100f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(44, 247, 239, 226) },
            )

            drawSectionLabel(canvas, "今日御签", 255f, serif)

            drawTorii(canvas, 850f, 300f, 320f, Color.argb(58, 247, 239, 226), strokeWidth = 2.5f)
            drawSakura(canvas, 958f, 250f, 18f, petal)
            drawSakura(canvas, 1000f, 548f, 14f, Color.argb(36, 247, 239, 226))
            drawSakura(canvas, 152f, 960f, 16f, Color.argb(30, 247, 239, 226))

            drawBand(canvas, data.band, serifBold)
            drawLuckNumber(canvas, data.luck, serif, serifBold)
            drawHairline(canvas, 830f)

            val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = creamSoft
                textSize = 24f
                typeface = serif
            }
            canvas.drawText("每日一签 · 明日再来", PAD.toFloat(), 884f, note)

            drawUser(canvas, data, avatar, serif, serifSemi)
            drawWaves(canvas)
            drawHairline(canvas, 1140f)
            drawFooter(canvas, data, serif, serifSemi)
            return bitmap
        } finally {
            avatar?.recycle()
        }
    }

    private fun drawHeader(
        canvas: Canvas,
        data: FortuneCardData,
        serif: Typeface,
        serifSemi: Typeface,
    ) {

        drawTorii(canvas, 100f, 76f, 56f, cream)

        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cream
            textSize = 38f
            typeface = serifSemi
        }
        canvas.drawText("有栖神社", 170f, 120f, name)

        val english = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = creamSoft
            textSize = 21f
            typeface = serif
        }
        canvas.drawText("Arisu Shrine", 170f, 152f, english)

        val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = creamSoft
            textSize = 23f
            typeface = serif
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(data.date, (WIDTH - PAD).toFloat(), 124f, date)
    }

    private fun drawHairline(canvas: Canvas, y: Float) {
        canvas.drawRect(
            PAD.toFloat(), y, (WIDTH - PAD).toFloat(), y + 1f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(58, 247, 239, 226) },
        )
    }

    private fun drawSectionLabel(canvas: Canvas, text: String, baseline: Float, serif: Typeface) {
        canvas.drawRect(
            PAD.toFloat(), baseline - 16f, PAD + 4f, baseline + 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold },
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = creamSoft
            textSize = 21f
            typeface = serif
        }
        canvas.drawText(text, PAD + 15f, baseline, paint)
    }

    private fun drawBand(canvas: Canvas, band: String, serifBold: Typeface) {
        val color = if (band == "大吉") gold else cream
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 118f
            typeface = serifBold
        }
        canvas.drawText(band, PAD.toFloat(), 404f, paint)
    }

    private fun drawLuckNumber(canvas: Canvas, luck: Int, serif: Typeface, serifBold: Typeface) {
        val text = luck.toString()
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cream
            textSize = 340f
            typeface = serifBold
        }
        canvas.drawText(text, PAD.toFloat(), 760f, number)

        val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = creamSoft
            textSize = 34f
            typeface = serif
        }
        val numberRight = PAD + number.measureText(text)
        canvas.drawText("/ 100", numberRight + 22f, 760f, unit)
    }

    private fun drawWaves(canvas: Canvas) {
        val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb(26, 247, 239, 226)
        }
        val y = 1050f
        var x = -30f
        while (x < WIDTH + 30f) {
            canvas.drawArc(RectF(x - 30f, y - 30f, x + 30f, y + 30f), 180f, 180f, false, wave)
            x += 60f
        }
    }

    private fun drawUser(
        canvas: Canvas,
        data: FortuneCardData,
        avatar: Bitmap?,
        serif: Typeface,
        serifSemi: Typeface,
    ) {
        if (data.userName.isBlank() && data.userNo.isBlank() && avatar == null) return
        val cx = 100f
        val cy = 958f
        val radius = 28f

        canvas.drawCircle(
            cx, cy, radius + 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = Color.argb(120, 247, 239, 226)
            },
        )
        if (avatar != null) {
            canvas.save()
            canvas.clipPath(
                Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) },
            )
            val src = centerCrop(avatar.width, avatar.height, radius * 2f, radius * 2f)
            canvas.drawBitmap(
                avatar,
                src,
                RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            canvas.restore()
        } else {
            canvas.drawCircle(
                cx, cy, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(34, 247, 239, 226) },
            )
            val initial = data.userName.firstOrNull()?.toString() ?: "有"
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cream
                textSize = 24f
                typeface = serifSemi
                textAlign = Paint.Align.CENTER
            }
            val fm = paint.fontMetrics
            canvas.drawText(initial, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        }

        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cream
            textSize = 26f
            typeface = serifSemi
        }
        canvas.drawText(data.userName.ifBlank { "社员" }, 144f, 956f, name)

        if (data.userNo.isNotBlank()) {
            val no = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = creamSoft
                textSize = 18f
                typeface = serif
            }
            canvas.drawText(data.userNo, 144f, 986f, no)
        }
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

    private val avatarClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun loadAvatar(raw: String?): Bitmap? {
        if (raw.isNullOrBlank()) return null
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/") -> Api.currentBaseUrl + raw
            else -> Api.currentBaseUrl + "/" + raw
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ArisuShrine-Share/1.0")
                .header("Accept", "image/*")
                .build()
            avatarClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size > 8 * 1024 * 1024) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, 200)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        data: FortuneCardData,
        serif: Typeface,
        serifSemi: Typeface,
    ) {
        val site = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cream
            textSize = 22f
            typeface = serifSemi
        }
        canvas.drawText("官网  arisushrine.com", PAD.toFloat(), 1198f, site)

        val repo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = creamSoft
            textSize = 20f
            typeface = serif
        }
        canvas.drawText("GitHub  github.com/Wojusensei/Arisu-Shrine", PAD.toFloat(), 1234f, repo)

        drawTorii(canvas, 984f, 1178f, 48f, Color.argb(170, 247, 239, 226))

        val serial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = creamSoft
            textSize = 18f
            typeface = serif
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("御神签 · ${data.date}", WIDTH / 2f, 1320f, serial)
    }

    private fun drawTorii(
        canvas: Canvas,
        cx: Float,
        top: Float,
        width: Float,
        color: Int,
        strokeWidth: Float = 0f,
    ) {
        val height = width * 0.62f
        val left = cx - width / 2f
        val right = cx + width / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (strokeWidth > 0f) {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }
        }

        val beam = Path().apply {
            moveTo(left - width * 0.03f, top)
            quadTo(cx, top - height * 0.08f, right + width * 0.03f, top)
            lineTo(right + width * 0.03f, top + height * 0.13f)
            quadTo(cx, top - height * 0.01f, left - width * 0.03f, top + height * 0.13f)
            close()
        }
        canvas.drawPath(beam, paint)

        val secondTop = top + height * 0.21f
        canvas.drawRect(
            left + width * 0.08f, secondTop,
            right - width * 0.08f, secondTop + height * 0.09f,
            paint,
        )
        val pillarTop = secondTop + height * 0.09f
        val pillarW = width * 0.06f
        canvas.drawRect(left + width * 0.18f, pillarTop, left + width * 0.18f + pillarW, top + height, paint)
        canvas.drawRect(right - width * 0.18f - pillarW, pillarTop, right - width * 0.18f, top + height, paint)
    }

    private fun drawSakura(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        repeat(5) { i ->
            val a = Math.toRadians(i * 72.0 - 90.0)
            val px = cx + cos(a).toFloat() * r * 0.52f
            val py = cy + sin(a).toFloat() * r * 0.52f
            canvas.drawCircle(px, py, r * 0.34f, paint)
        }
        canvas.drawCircle(
            cx, cy, r * 0.14f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(80, 247, 239, 226) },
        )
    }
}
