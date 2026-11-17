package dev.arisu.shrine.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.google.zxing.common.BitMatrix
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

/** 卡面内容开关；默认显示签文、宜忌与连续天数，二维码按需开启。 */
data class FortuneCardOptions(
    val showVerse: Boolean = true,
    val showYiJi: Boolean = true,
    val showStreak: Boolean = true,
    val showQr: Boolean = false,
)

/** 今日御签卡的内容；签文与宜忌随卡面自带，不从服务端取。 */
data class FortuneCardData(
    val luck: Int,
    val band: String,
    /** yyyy.MM.dd，按上海时区由调用方算好 */
    val date: String,
    val userName: String = "",
    val userNo: String = "",
    val avatarUrl: String? = null,
    val streak: Int = 0,
    val options: FortuneCardOptions = FortuneCardOptions(),
    val style: FortuneCardStyle = FortuneCardStyle.LACQUER,
)

/** 从当前社员构造卡面身份；昵称、编号、头像都只取这一份，不混用其它来源。 */
fun fortuneCardData(
    user: User?,
    luck: Int,
    band: String,
    date: String,
    streak: Int = 0,
    options: FortuneCardOptions = FortuneCardOptions(),
    style: FortuneCardStyle = FortuneCardStyle.LACQUER,
): FortuneCardData =
    FortuneCardData(
        luck = luck,
        band = band,
        date = date,
        userName = user?.nickname.orEmpty(),
        userNo = user?.id.orEmpty(),
        avatarUrl = user?.avatarUrl,
        streak = streak,
        options = options,
        style = style,
    )

/**
 * 御签分享卡。朱底米白字的正式签面：大数字在左，鸟居线稿在右，
 * 靠留白与字号撑气势；没有大圆饼、红底字块、渐变和阴影。
 */
object FortuneCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val PAD = 72
    private const val SITE_URL = "https://arisushrine.com"

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

    internal fun render(context: Context, data: FortuneCardData, width: Int = WIDTH): Bitmap {
        val serif = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_400), Typeface.NORMAL)
        val serifSemi = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_600), Typeface.NORMAL)
        val serifBold = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_serif_700), Typeface.NORMAL)
        val palette = paletteFor(data.style)
        val scale = width.toFloat() / WIDTH

        val bitmap = createBitmap(width, (HEIGHT * scale).toInt())
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.drawColor(palette.paper)

        val avatar = loadAvatar(data.avatarUrl)
        drawHeader(canvas, data, serif, serifSemi, palette)
        drawHairline(canvas, 205f, palette.line)

        // 左侧一条很淡的装订线，给版面一个脊
        canvas.drawRect(
            60f, 240f, 62f, 1100f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(palette.ink, 44) },
        )

        drawSectionLabel(canvas, "今日御签", 255f, serif, palette)

        // 右侧：平面鸟居线稿，和左侧数字对望；压在签文上方，别让立柱穿字
        drawTorii(canvas, 850f, 270f, 300f, palette.pattern, strokeWidth = 2.5f)
        drawSakura(canvas, 958f, 250f, 18f, palette.petal, alpha(palette.ink, 80))
        drawSakura(canvas, 1000f, 548f, 14f, palette.patternFaint, alpha(palette.ink, 80))
        drawSakura(canvas, 152f, 960f, 16f, palette.patternFaint, alpha(palette.ink, 80))

        drawBand(canvas, data.band, serifBold, palette)
        drawLuckNumber(canvas, data.luck, serif, serifBold, palette)
        drawFortuneText(canvas, data, serif, serifSemi, palette)
        drawHairline(canvas, 830f, palette.line)

        val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 24f
            typeface = serif
        }
        canvas.drawText("每日一签 · 明日再来", PAD.toFloat(), 884f, note)
        if (data.options.showStreak && data.streak > 0) {
            val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.inkSoft
                textSize = 22f
                typeface = serif
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("连续求签 ${data.streak} 天", (WIDTH - PAD).toFloat(), 884f, streak)
        }

        drawUser(canvas, data, avatar, serif, serifSemi, palette)
        drawWaves(canvas, palette)
        drawHairline(canvas, 1140f, palette.line)
        drawFooter(canvas, data, serif, serifSemi, palette)
        return bitmap
    }

    private fun drawHeader(
        canvas: Canvas,
        data: FortuneCardData,
        serif: Typeface,
        serifSemi: Typeface,
        palette: FortunePalette,
    ) {
        // 应用图标的鸟居：平涂，不套底方块
        drawTorii(canvas, 100f, 76f, 56f, palette.ink)

        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ink
            textSize = 38f
            typeface = serifSemi
        }
        canvas.drawText("有栖神社", 170f, 120f, name)

        val english = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 21f
            typeface = serif
        }
        canvas.drawText("Arisu Shrine", 170f, 152f, english)

        val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 23f
            typeface = serif
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(data.date, (WIDTH - PAD).toFloat(), 124f, date)
    }

    private fun drawHairline(canvas: Canvas, y: Float, color: Int) {
        canvas.drawRect(
            PAD.toFloat(), y, (WIDTH - PAD).toFloat(), y + 1f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )
    }

    private fun drawSectionLabel(
        canvas: Canvas,
        text: String,
        baseline: Float,
        serif: Typeface,
        palette: FortunePalette,
    ) {
        canvas.drawRect(
            PAD.toFloat(), baseline - 16f, PAD + 4f, baseline + 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 21f
            typeface = serif
        }
        canvas.drawText(text, PAD + 15f, baseline, paint)
    }

    private fun drawBand(
        canvas: Canvas,
        band: String,
        serifBold: Typeface,
        palette: FortunePalette,
    ) {
        val color = if (band == "大吉") palette.accent else palette.ink
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 118f
            typeface = serifBold
        }
        canvas.drawText(band, PAD.toFloat(), 404f, paint)
    }

    private fun drawLuckNumber(
        canvas: Canvas,
        luck: Int,
        serif: Typeface,
        serifBold: Typeface,
        palette: FortunePalette,
    ) {
        val text = luck.toString()
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ink
            textSize = 340f
            typeface = serifBold
        }
        canvas.drawText(text, PAD.toFloat(), 760f, number)

        val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 34f
            typeface = serif
        }
        val numberRight = PAD + number.measureText(text)
        canvas.drawText("/ 100", numberRight + 22f, 760f, unit)
    }

    private fun drawFortuneText(
        canvas: Canvas,
        data: FortuneCardData,
        serif: Typeface,
        serifSemi: Typeface,
        palette: FortunePalette,
    ) {
        val text = fortuneText(data.band)
        if (data.options.showVerse && text.verse.isNotEmpty()) {
            val verse = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = alpha(palette.ink, 230)
                textSize = 36f
                typeface = serif
                textAlign = Paint.Align.RIGHT
            }
            text.verse.take(2).forEachIndexed { i, line ->
                canvas.drawText(line, (WIDTH - PAD).toFloat(), 512f + i * 48f, verse)
            }
        }
        if (data.options.showYiJi && text.yi.isNotBlank() && text.ji.isNotBlank()) {
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.inkSoft
                textSize = 24f
                typeface = serif
                textAlign = Paint.Align.RIGHT
            }
            val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.ink
                textSize = 28f
                typeface = serifSemi
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("宜", 936f, 664f, label)
            canvas.drawText(text.yi, (WIDTH - PAD).toFloat(), 664f, value)
            canvas.drawText("忌", 936f, 708f, label)
            canvas.drawText(text.ji, (WIDTH - PAD).toFloat(), 708f, value)
        }
    }

    private fun drawWaves(canvas: Canvas, palette: FortunePalette) {
        val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = palette.patternFaint
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
        palette: FortunePalette,
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
                color = alpha(palette.ink, 120)
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
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(palette.ink, 34) },
            )
            val initial = data.userName.firstOrNull()?.toString() ?: "有"
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.ink
                textSize = 24f
                typeface = serifSemi
                textAlign = Paint.Align.CENTER
            }
            val fm = paint.fontMetrics
            canvas.drawText(initial, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        }

        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ink
            textSize = 26f
            typeface = serifSemi
        }
        canvas.drawText(data.userName.ifBlank { "社员" }, 144f, 956f, name)

        if (data.userNo.isNotBlank()) {
            val no = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.inkSoft
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

    /** 预览和出图会反复画同一张头像，缓存起来别每次都走网络 */
    private val avatarCache = LruCache<String, Bitmap>(8)

    /** 头像取不到就退回首字圆章，卡片不能因为一张头像挂掉 */
    private fun loadAvatar(raw: String?): Bitmap? {
        if (raw.isNullOrBlank()) return null
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/") -> Api.currentBaseUrl + raw
            else -> Api.currentBaseUrl + "/" + raw
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        avatarCache.get(url)?.let { return it }
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
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.also {
                    avatarCache.put(url, it)
                }
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
        palette: FortunePalette,
    ) {
        val site = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ink
            textSize = 22f
            typeface = serifSemi
        }
        canvas.drawText("官网  arisushrine.com", PAD.toFloat(), 1198f, site)

        val repo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 20f
            typeface = serif
        }
        canvas.drawText("GitHub  github.com/Wojusensei/Arisu-Shrine", PAD.toFloat(), 1234f, repo)

        val qr = if (data.options.showQr) QrCode.matrix(SITE_URL) else null
        if (qr != null) {
            drawQr(canvas, qr, palette)
        } else {
            drawTorii(canvas, 984f, 1178f, 48f, alpha(palette.ink, 170))
        }

        val serial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.inkSoft
            textSize = 18f
            typeface = serif
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("御神签 · ${data.date}", WIDTH / 2f, 1320f, serial)
    }

    /** 二维码直接印在卡面上，不铺底、不描边；卡面自身就是静区。 */
    private fun drawQr(canvas: Canvas, matrix: BitMatrix, palette: FortunePalette) {
        val box = 150f
        val cell = (box / matrix.width).toInt().coerceAtLeast(1).toFloat()
        val size = cell * matrix.width
        val left = WIDTH - PAD - size
        val top = 1122f + (box - size) / 2f
        val paint = Paint().apply { color = palette.qrInk }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (!matrix.get(x, y)) continue
                canvas.drawRect(
                    left + x * cell,
                    top + y * cell,
                    left + (x + 1) * cell,
                    top + (y + 1) * cell,
                    paint,
                )
            }
        }
    }

    /**
     * 平面鸟居：上梁带一点弧，二梁与两根立柱。
     * [strokeWidth] 大于 0 时画线稿，否则平涂。
     */
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

    private fun drawSakura(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        color: Int,
        centerColor: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        repeat(5) { i ->
            val a = Math.toRadians(i * 72.0 - 90.0)
            val px = cx + cos(a).toFloat() * r * 0.52f
            val py = cy + sin(a).toFloat() * r * 0.52f
            canvas.drawCircle(px, py, r * 0.34f, paint)
        }
        canvas.drawCircle(
            cx, cy, r * 0.14f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = centerColor },
        )
    }

    private fun alpha(color: Int, a: Int): Int =
        (color and 0x00FFFFFF) or (a shl 24)
}
