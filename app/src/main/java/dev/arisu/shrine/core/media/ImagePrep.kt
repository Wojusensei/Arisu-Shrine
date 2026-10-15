package dev.arisu.shrine.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

private val PLAIN_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

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
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
    }
    val src = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    return rotateBitmap(src, readExifDegrees(file))
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
    if (mime == "image/gif") return@withContext raw

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(raw.absolutePath, bounds)
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (!needsRecompress(mime, longEdge, raw.length()) && mime in PLAIN_TYPES) {
        return@withContext raw
    }

    val decoded = runCatching { decodeForEdit(raw, UPLOAD_MAX_DIM) }.getOrNull()
        ?: if (mime in PLAIN_TYPES) return@withContext raw
        else throw ApiError("识别不出这张图片的格式，请换一张试试")

    val bmp = scaleDownTo(decoded, UPLOAD_MAX_DIM)
    val keepAlpha = mime == "image/png" && bmp.hasAlpha()

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

    if (!ok || !recompressWorthIt(out.length(), raw.length())) {
        out.delete()
        return@withContext raw
    }
    raw.delete()
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
