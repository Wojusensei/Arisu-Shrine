package dev.arisu.shrine.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.arisu.shrine.data.remote.ApiError
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * 图片上下行前的整备：解码取样、EXIF 转正、压缩、存相册。
 * 帖子/头像/表情包三处的图都从这里过一道，规则只写一份。
 */

/** 上传前压到的长边；再大服务端与 Caddy 的两道体积闸也会先卡住 */
const val UPLOAD_MAX_DIM = 2048

/** 小于这个体积且不超长边的图不折腾，原样上传 */
private const val KEEP_RAW_BYTES = 700L * 1024

/** 按文件头认图，不信任 ContentResolver 的 MIME。
 * 部分相册/网盘会把 HEIC/AVIF 标成 image/jpeg；这类文件原名带 .jpg 上传，
 * 服务端魔数校验会按「不是图片」退回 400。
 */
fun sniffImageKind(head: ByteArray): String? {
    fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xff else -1
    if (at(0) == 0xff && at(1) == 0xd8 && at(2) == 0xff) return "jpeg"
    if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4e && at(3) == 0x47 &&
        at(4) == 0x0d && at(5) == 0x0a && at(6) == 0x1a && at(7) == 0x0a
    ) return "png"
    if (at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == '8'.code) return "gif"
    if (at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code &&
        at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code
    ) return "webp"
    if (at(4) == 'f'.code && at(5) == 't'.code && at(6) == 'y'.code && at(7) == 'p'.code) {
        val brand = String(head, 8, minOf(4, head.size - 8), Charsets.US_ASCII)
        if (brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis")) {
            return "heif"
        }
    }
    return null
}

fun sniffImageKind(file: File): String? = runCatching {
    file.inputStream().use { input ->
        val head = ByteArray(12)
        var n = 0
        // 单次 read 可能只给几个字节；魔数短读会把 JPEG/HEIC 误判成未知格式
        while (n < head.size) {
            val read = input.read(head, n, head.size - n)
            if (read < 0) break
            n += read
        }
        if (n <= 0) null else sniffImageKind(head.copyOf(n))
    }
}.getOrNull()

private fun kindExt(kind: String?): String = when (kind) {
    "png" -> "png"
    "gif" -> "gif"
    "webp" -> "webp"
    else -> "jpg"
}

/** 按真实格式改名；改名失败就复制一份，避免把 WebP 顶着 .jpg 发出去。 */
private fun renamedWithExt(file: File, kind: String?): File {
    val ext = kindExt(kind)
    if (file.extension.equals(ext, ignoreCase = true)) return file
    val target = File(file.parentFile, "${file.nameWithoutExtension}.$ext")
    if (file.renameTo(target)) return target
    return runCatching {
        target.outputStream().use { out -> file.inputStream().use { it.copyTo(out) } }
        file.delete()
        target
    }.getOrDefault(file)
}

/** MIME → 扩展名。服务端只收 jpg/jpeg/png/gif/webp，其余都按 jpg 名交上去 */
fun extForMime(mime: String?): String = when (mime?.lowercase()) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    else -> "jpg"
}

/** 2 的幂次采样：解出来的长边落在 [maxDim, 2*maxDim) 内，不会整图爆内存 */
fun imageSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxDim) sample *= 2
    return sample
}

/** 顺时针旋转；90 的倍数之外会做插值，调用方只传 90/180/270 */
fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return src
    val rotated = Bitmap.createBitmap(
        src, 0, 0, src.width, src.height,
        android.graphics.Matrix().apply { postRotate(degrees.toFloat()) },
        true,
    )
    if (rotated !== src) src.recycle()
    return rotated
}

/** EXIF 方向 → 顺时针角度；读不出（PNG 等无 EXIF 的格式）就是 0 */
fun readExifDegrees(file: File): Int = runCatching {
    val orientation = android.media.ExifInterface(file.absolutePath)
        .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}.getOrDefault(0)

/**
 * 解码一张本地图供编辑/再压缩：两段式降采样（先只读尺寸）后按 EXIF 转正。
 * 相机竖拍的照片带 Orientation 标记、像素本身是躺着的，不转正会裁出一张歪图。
 */
fun decodeForEdit(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return decodeWithImageDecoder(file, maxDim)
    val opts = BitmapFactory.Options().apply {
        inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
    }
    val src = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return decodeWithImageDecoder(file, maxDim)
    return rotateBitmap(src, readExifDegrees(file))
}

/**
 * HEIC/AVIF 在部分机型上 BitmapFactory 直接返回 null；API 28+ 用 ImageDecoder 再试一次。
 * ImageDecoder 会自己处理 EXIF 方向，这里不再二次旋转。
 */
private fun decodeWithImageDecoder(file: File, maxDim: Int): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.setTargetSampleSize(imageSampleSize(info.size.width, info.size.height, maxDim))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}

/** 饼图式的「长边不超过 maxDim」缩放；已经够小就原样返回 */
fun scaleDownTo(bmp: Bitmap, maxDim: Int): Bitmap {
    val long = maxOf(bmp.width, bmp.height)
    if (long <= maxDim) return bmp
    val ratio = maxDim.toFloat() / long
    val scaled = Bitmap.createScaledBitmap(
        bmp,
        (bmp.width * ratio).roundToInt().coerceAtLeast(1),
        (bmp.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== bmp) bmp.recycle()
    return scaled
}

/** 写 JPEG 到缓存目录的 upload/ 下，返回文件 */
fun writeJpegToCache(
    context: Context,
    bmp: Bitmap,
    prefix: String,
    quality: Int = 88,
    maxDim: Int = UPLOAD_MAX_DIM,
): File {
    val dir = File(context.cacheDir, "upload").apply { mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    val scaled = scaleDownTo(bmp, maxDim)
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    if (scaled !== bmp) scaled.recycle()
    return file
}

/**
 * 选图 → 可直接上传的暂存文件。
 *
 * 手机原图动辄 5–15MB（服务端 10MB、Caddy 12MB 两道闸），不压就是拿上传赌运气；
 * 压到长边 ≤2048 后典型只剩几百 KB。GIF 原样保留——重编码会把动图压成静帧。
 */
suspend fun stagePickedImage(context: Context, uri: Uri, dir: File): File = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    val raw = File(dir, "picked_${System.currentTimeMillis()}.${extForMime(mime)}")
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            raw.outputStream().use { output -> input.copyTo(output) }
        } ?: throw ApiError("读取图片失败，请换一张试试")
    }.onFailure {
        raw.delete()
        // 本地读不出来（授权过期、文件已被删）也走这里：不能把本地 IO 失败
        // 伪装成「连接服务器失败」，用户会去查网络
        throw if (it is ApiError) it else ApiError("读取图片失败，请换一张试试")
    }
    val kind = sniffImageKind(raw)
    if (kind == "gif") return@withContext renamedWithExt(raw, "gif")

    val plainKind = kind == "jpeg" || kind == "png" || kind == "webp"
    val namedRaw = if (plainKind) renamedWithExt(raw, kind) else raw
    val kindMime = when (kind) {
        "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> ""
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(namedRaw.absolutePath, bounds)
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    // 只有魔数与声明都对得上的 jpg/png/webp 才允许原样上传；
    // HEIC/AVIF 即使相册谎报 image/jpeg，也一律解码重编码成服务端认识的格式
    if (plainKind && !needsRecompress(kindMime, longEdge, namedRaw.length())) {
        return@withContext namedRaw
    }

    val decoded = runCatching { decodeForEdit(namedRaw, UPLOAD_MAX_DIM) }.getOrNull()
        ?: if (plainKind) return@withContext namedRaw
        else throw ApiError("这张图片的格式暂不支持，请换一张试试")
    // 取样是 2 的幂，解出来可能仍大于目标（2400 只会原样解出），必须再按比例缩一次
    val bmp = scaleDownTo(decoded, UPLOAD_MAX_DIM)
    val keepAlpha = bmp.hasAlpha() && (kind == "png" || mime == "image/png")
    // 扩展名先落变量：模板串里再嵌引号会被开源导出器的注释剥离器截断（见导出管线陷阱）
    val ext = if (keepAlpha) "png" else "jpg"
    val out = File(dir, "upload_${System.currentTimeMillis()}.$ext")
    val ok = out.outputStream().use { stream ->
        bmp.compress(
            if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
            if (keepAlpha) 100 else 85,
            stream,
        )
    }
    bmp.recycle()
    if (!ok) {
        out.delete()
        if (plainKind) return@withContext namedRaw
        throw ApiError("图片处理失败，请换一张试试")
    }
    // 压完反而更大（小图、本就是高压缩比源图）就用原件：压缩只为传得上去，不为折腾。
    // 但 HEIC/AVIF 这类原件服务端不认，再大也得用转出来的 jpg。
    if (plainKind && !recompressWorthIt(out.length(), namedRaw.length())) {
        out.delete()
        return@withContext namedRaw
    }
    namedRaw.delete()
    out
}

/** 要不要重编码：动图永远原样（重编码等于杀掉动画），其余看长边与体积 */
fun needsRecompress(mime: String, longEdge: Int, bytes: Long): Boolean =
    mime != "image/gif" && (longEdge <= 0 || longEdge > UPLOAD_MAX_DIM || bytes > KEEP_RAW_BYTES)

/** 压出来的件比原件小才值得用；空文件与变大都算不值 */
fun recompressWorthIt(outBytes: Long, rawBytes: Long): Boolean =
    outBytes in 1 until rawBytes

/* ══════════════════════════════════════════════════════════════════
 * 存相册
 * ══════════════════════════════════════════════════════════════════ */

/** 下载用裸 client：带 baseRewrite 的 Api.client 会把外链图片改写到本站域名，用不得 */
private val downloadClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

/** 26–28 写相册要申请存储权限；29+ 走 MediaStore 挂起写，不需要 */
fun needsLegacyGalleryPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

/**
 * 把本站图取回本地暂存件。
 *
 * 取景器只认本地文件：改旧帖的封面要先把它下载下来，
 * 才能在取景器里重新框一次显示范围。
 */
suspend fun downloadImageToFile(url: String, dir: File): File = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        .takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp") } ?: "jpg"
    val file = File(dir, "remote_${System.currentTimeMillis()}.$ext")
    runCatching {
        val request = okhttp3.Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiError("图片下载失败，请稍后再试")
            val stream = resp.body?.byteStream() ?: throw ApiError("图片下载失败，请稍后再试")
            stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        }
    }.onFailure {
        file.delete()
        // 本地 IO 失败不能伪装成「连接服务器失败」，用户会去查网络
        throw if (it is ApiError) it else ApiError("图片下载失败，请稍后再试")
    }
    if (file.length() == 0L) {
        file.delete()
        throw ApiError("图片下载失败，请稍后再试")
    }
    file
}

/**
 * 把原图下载后存进系统相册（存的是原文件，不是屏幕上那张缩放图）。
 *
 * API 29+ 走 MediaStore 挂起写，不需要任何权限；26–28 需要写外部存储权限，
 * 由调用方按 [needsLegacyGalleryPermission] 先申请。
 */
suspend fun saveImageToGallery(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
    val bytes = runCatching {
        val request = okhttp3.Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.bytes()
        }
    }.getOrNull() ?: throw ApiError("图片下载失败，请稍后再试")
    if (bytes.isEmpty()) throw ApiError("图片下载失败，请稍后再试")

    val mime = when {
        url.endsWith(".png", true) -> "image/png"
        url.endsWith(".gif", true) -> "image/gif"
        url.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }
    val ext = extForMime(mime)
    val name = "arisu_${System.currentTimeMillis()}.$ext"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw ApiError("存相册失败，请稍后再试")
        runCatching {
            context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
        }.onFailure {
            context.contentResolver.delete(target, null, null)
            throw ApiError("存相册失败，请稍后再试")
        }
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        context.contentResolver.update(target, done, null, null)
        return@withContext true
    }

    // 26–28：写公共图片目录再让扫描器收录（需要 WRITE_EXTERNAL_STORAGE）
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ArisuShrine")
    if (!dir.exists() && !dir.mkdirs()) throw ApiError("存相册失败，请稍后再试")
    val file = File(dir, name)
    runCatching { file.outputStream().use { it.write(bytes) } }
        .onFailure { throw ApiError("存相册失败，请检查存储权限后再试") }
    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
    true
}

/**
 * 把本地生成的位图（分享卡、御签卡）存进系统相册。
 * API 29+ 走 MediaStore，26–28 需要写外部存储权限，调用方按 [needsLegacyGalleryPermission] 先申请。
 * 不回收传入的 bitmap，由调用方负责。
 */
suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, prefix: String): Boolean =
    withContext(Dispatchers.IO) {
        val safe = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val name = "${safe}_${System.currentTimeMillis()}.png"
        val mime = "image/png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/ArisuShrine",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw ApiError("存相册失败，请稍后再试")
            runCatching {
                context.contentResolver.openOutputStream(target)?.use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw ApiError("存相册失败，请稍后再试")
                    }
                } ?: throw ApiError("存相册失败，请稍后再试")
            }.onFailure {
                context.contentResolver.delete(target, null, null)
                throw if (it is ApiError) it else ApiError("存相册失败，请稍后再试")
            }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(target, done, null, null)
            return@withContext true
        }

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "ArisuShrine",
        )
        if (!dir.exists() && !dir.mkdirs()) throw ApiError("存相册失败，请稍后再试")
        val file = File(dir, name)
        runCatching {
            file.outputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw ApiError("存相册失败，请稍后再试")
                }
            }
        }.onFailure {
            throw if (it is ApiError) it else ApiError("存相册失败，请检查存储权限后再试")
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        true
    }
