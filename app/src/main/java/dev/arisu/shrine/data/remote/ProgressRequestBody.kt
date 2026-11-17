package dev.arisu.shrine.data.remote

import java.io.File
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source

/**
 * 带进度的上传体：分片写进连接，边写边回报已发送字节。
 *
 * 回调发生在 OkHttp 的 IO 线程（每个百分点一次，不是每个分片一次）——
 * 回调方直接写 Compose 状态即可，快照状态允许跨线程写。
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (sent: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length().coerceAtLeast(1L)
        var sent = 0L
        var lastPercent = -1
        file.source().use { source ->
            val buffer = Buffer()
            while (true) {
                val read = source.read(buffer, CHUNK)
                if (read == -1L) break
                sink.write(buffer, read)
                sent += read
                val percent = (sent * 100 / total).toInt()
                // 8KB 一片，10MB 的图会有上千次回调：只在百分点变化时出声
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(sent, total)
                }
            }
        }
    }

    private companion object {
        const val CHUNK = 8 * 1024L
    }
}
