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

const val UPLOAD_MAX_DIM = 2048

private const val KEEP_RAW_BYTES = 700L * 1024

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
        val n = input.read(head)
        if (n <= 0) null else sniffImageKind(head.copyOf(n))
    }
}.getOrNull()

private fun kindExt(kind: String?): String = when (kind) {
    "png" -> "png"
    "gif" -> "gif"
    "webp" -> "webp"
    else -> "jpg"
}

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

fun extForMime(mime: String?): String = when (mime?.lowercase()) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    else -> "jpg"
}

fun imageSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxDim) sample *= 2
    return sample
}

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

private fun decodeWithImageDecoder(file: File, maxDim: Int): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.setTargetSampleSize(imageSampleSize(info.size.width, info.size.height, maxDim))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}

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

    if (plainKind && !needsRecompress(kindMime, longEdge, namedRaw.length())) {
        return@withContext namedRaw
    }

    val decoded = runCatching { decodeForEdit(namedRaw, UPLOAD_MAX_DIM) }.getOrNull()
        ?: if (plainKind) return@withContext namedRaw
        else throw ApiError("这张图片的格式暂不支持，请换一张试试")

    val bmp = scaleDownTo(decoded, UPLOAD_MAX_DIM)
    val keepAlpha = bmp.hasAlpha() && (kind == "png" || mime == "image/png")

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

    if (plainKind && !recompressWorthIt(out.length(), namedRaw.length())) {
        out.delete()
        return@withContext namedRaw
    }
    namedRaw.delete()
    out
}

fun needsRecompress(mime: String, longEdge: Int, bytes: Long): Boolean =
    mime != "image/gif" && (longEdge <= 0 || longEdge > UPLOAD_MAX_DIM || bytes > KEEP_RAW_BYTES)

fun recompressWorthIt(outBytes: Long, rawBytes: Long): Boolean =
    outBytes in 1 until rawBytes

private val downloadClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

fun needsLegacyGalleryPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

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

        throw if (it is ApiError) it else ApiError("图片下载失败，请稍后再试")
    }
    if (file.length() == 0L) {
        file.delete()
        throw ApiError("图片下载失败，请稍后再试")
    }
    file
}

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

    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ArisuShrine")
    if (!dir.exists() && !dir.mkdirs()) throw ApiError("存相册失败，请稍后再试")
    val file = File(dir, name)
    runCatching { file.outputStream().use { it.write(bytes) } }
        .onFailure { throw ApiError("存相册失败，请检查存储权限后再试") }
    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
    true
}
