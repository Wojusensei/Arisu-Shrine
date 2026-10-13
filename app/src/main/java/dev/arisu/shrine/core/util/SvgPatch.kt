package dev.arisu.shrine.core.util

import okio.ByteString.Companion.decodeBase64

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

fun patchSvgText(text: String): String = injectViewBox(inlineDataImages(text))

private fun inlineDataImages(svg: String): String {
    if (!svg.contains("<image", ignoreCase = true)) return svg
    return svg.replace(imageTagRe) { m ->
        val attrs = attrRe.findAll(m.groupValues[1])
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val href = attrs["href"] ?: attrs["xlink:href"] ?: return@replace ""
        val payload = href.substringAfter("base64,", "")
        val inner = payload.takeIf { it != href }?.decodeBase64()?.utf8().orEmpty()

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
