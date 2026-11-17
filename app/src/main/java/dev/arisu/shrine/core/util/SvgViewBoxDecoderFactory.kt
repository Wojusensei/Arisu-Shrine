package dev.arisu.shrine.core.util

import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.svg.SvgDecoder
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * 解码前的 SVG 文本改写（见 [patchSvgText]：摊平内联 logo、补 viewBox），
 * 包装 [SvgDecoder.Factory]。超过 2MB 或非 SVG 内容原样放行，改写结果按内容哈希落缓存。
 */
class SvgViewBoxDecoderFactory(
    private val config: SvgDecoder.Factory,
) : Decoder.Factory {

    override fun create(
        data: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        // coil3 的 Decoder 在 create 时即绑定源（decode 无参），
        // 因此把改写后的源交给 delegate 创建即完成绑定
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
            // 不足 512 字节：全部读出（耗尽式读取不会抛 EOF）。
            // 小文件同样要判型 + 补丁：手写小图标常不到 512B，跳过会按
            // 「1 单位 = 1 物理像素」解码，在定高裁切框里发虚
            if (buffered.buffer.size == 0L) return null
            val text = buffered.readByteString().utf8()
            if (!text.contains("<svg", ignoreCase = true) && !text.contains("<?xml", ignoreCase = true)) {
                return null
            }
            return rebuiltSource(patchSvgText(text), data, options)
        }
        // ≥512：peek 头部判型（peek 不消费，原源完好交给下游）
        val head = buffered.peek().readByteString(512).utf8()
        if (!head.contains("<svg", ignoreCase = true) && !head.contains("<?xml", ignoreCase = true)) {
            return null
        }
        // 2MB 上限：request 返回 true 说明还有超出部分，不处理
        if (buffered.request(2L * 1024 * 1024)) return null
        val text = buffered.readByteString().utf8()
        return rebuiltSource(patchSvgText(text), data, options)
    }

    /** 改写（或原样）的内容落缓存文件后重建 ImageSource；metadata 透传原源。 */
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
