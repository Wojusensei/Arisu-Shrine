package dev.arisu.shrine.data.remote

import java.io.File
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source

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
