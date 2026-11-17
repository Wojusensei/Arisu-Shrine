package dev.arisu.shrine.core.util

import okio.ByteString.Companion.decodeBase64

/**
 * 外站 SVG 的落地改写：摊平 base64 内联的 logo，并给缺 viewBox 的补上。
 *
 * shields.io 徽章把 logo 放在 `<image href="data:image/svg+xml;base64,…">` 里，
 * 而下游解码器（AndroidSVG）只认位图的 data URI，这一层会被整块跳过——
 * 徽章渲染出来就只剩文字，GitHub 上的图标全不见（用户比对截图反馈）。
 * 这里把内层 SVG 解出来，按 `<image>` 的 x/y/width/height 与内层 viewBox
 * 换算成 `<g transform>`，交出一组真路径；描色沿用内层根元素的 fill。
 *
 * 补 viewBox 解决的是另一件事：`<svg width="58" height="20">`（无 viewBox）
 * 一律按「1 单位 = 1 物理像素」渲染、无视盒子缩放，徽章会缩成一点。
 */
private val imageTagRe = Regex("""<image\b([^>]*?)/?>""", RegexOption.IGNORE_CASE)
private val attrRe = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*"([^"]*)"""")
private val innerViewBoxRe = Regex("""viewBox\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val innerFillRe = Regex("""<svg\b[^>]*\bfill\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val xmlDeclRe = Regex("""(?is)<\?xml.*?\?>""")
private val innerTitleRe = Regex("""(?is)<title>.*?</title>""")
private val svgOpenRe = Regex("""(?is)<svg\b[^>]*>""")
private val svgCloseRe = Regex("""(?is)</svg\s*>\s*$""")
private val viewBoxAttrRe = Regex("""viewBox\s*=\s*["']""", RegexOption.IGNORE_CASE)
private val widthAttrRe = Regex("""width\s*=\s*"([\d.]+)"""")
private val heightAttrRe = Regex("""height\s*=\s*"([\d.]+)"""")

/** 改写一份 SVG 文本；认不出的原样返回。 */
fun patchSvgText(text: String): String = injectViewBox(inlineDataImages(text))

private fun inlineDataImages(svg: String): String {
    if (!svg.contains("<image", ignoreCase = true)) return svg
    return svg.replace(imageTagRe) { m ->
        val attrs = attrRe.findAll(m.groupValues[1])
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val href = attrs["href"] ?: attrs["xlink:href"] ?: return@replace ""
        val payload = href.substringAfter("base64,", "")
        val inner = payload.takeIf { it != href }?.decodeBase64()?.utf8().orEmpty()
        // 位图（png/gif）的内联图原样留着：解码器自己认，只是 SVG 内层它不认
        if (!inner.contains("<svg", ignoreCase = true)) return@replace m.value

        val box = innerViewBoxRe.find(inner)?.groupValues?.get(1)?.trim()?.split(Regex("\\s+"))
        val boxW = box?.getOrNull(2)?.toFloatOrNull()
        val boxH = box?.getOrNull(3)?.toFloatOrNull()
        if (boxW == null || boxH == null || boxW <= 0f || boxH <= 0f) return@replace ""

        val w = attrs["width"]?.toFloatOrNull() ?: boxW
        val h = attrs["height"]?.toFloatOrNull() ?: boxH
        val x = attrs["x"]?.toFloatOrNull() ?: 0f
        val y = attrs["y"]?.toFloatOrNull() ?: 0f
        val body = inner.replace(xmlDeclRe, "")
            .replace(innerTitleRe, "")
            .replaceFirst(svgOpenRe, "")
            .replaceFirst(svgCloseRe, "")
        if (body.isBlank()) return@replace ""

        val fill = innerFillRe.find(inner)?.groupValues?.get(1)?.let { " fill=\"$it\"" }.orEmpty()
        val sx = w / boxW
        val sy = h / boxH
        "<g transform=\"translate($x,$y) scale($sx,$sy)\"$fill>$body</g>"
    }
}

private fun injectViewBox(svg: String): String {
    if (viewBoxAttrRe.containsMatchIn(svg)) return svg
    val w = widthAttrRe.find(svg)?.groupValues?.get(1) ?: return svg
    val h = heightAttrRe.find(svg)?.groupValues?.get(1) ?: return svg
    return svg.replaceFirst("<svg", "<svg viewBox=\"0 0 $w $h\"", ignoreCase = true)
}
