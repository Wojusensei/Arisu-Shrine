package dev.arisu.shrine.core.util

import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.svg.SvgDecoder
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

class SvgViewBoxDecoderFactory(
    private val config: SvgDecoder.Factory,
) : Decoder.Factory {

    override fun create(
        data: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {

        val patched = runCatching { patchSource(data, options) }.getOrNull()
        return if (patched == null) {
            config.create(data, options, imageLoader)
        } else {
            config.create(patched, options, imageLoader)
        }
    }

    private fun patchSource(data: SourceFetchResult, options: Options): SourceFetchResult? {
        val buffered = data.source.source().buffer()
        if (!buffered.request(512)) {

            if (buffered.buffer.size == 0L) return null
            return rebuiltSource(buffered.readByteString().utf8(), data, options)
        }

        val head = buffered.peek().readByteString(512).utf8()
        if (!head.contains("<svg", ignoreCase = true) && !head.contains("<?xml", ignoreCase = true)) {
            return null
        }

        if (buffered.request(2L * 1024 * 1024)) return null
        val text = buffered.readByteString().utf8()
        return rebuiltSource(patchSvgText(text), data, options)
    }

    private fun rebuiltSource(
        body: String,
        data: SourceFetchResult,
        options: Options,
    ): SourceFetchResult {
        val dir = File(options.context.cacheDir, "svg_viewbox").apply { mkdirs() }
        val file = File(dir, "${body.hashCode()}.svg")
        if (!file.exists()) file.writeText(body)
        val src = coil3.decode.ImageSource(
            file.absolutePath.toPath(),
            FileSystem.SYSTEM,
            data.mimeType,
            null,
            data.source.metadata,
        )
        return SourceFetchResult(src, data.mimeType, data.dataSource)
    }
}
