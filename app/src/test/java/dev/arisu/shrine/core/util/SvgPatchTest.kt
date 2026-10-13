package dev.arisu.shrine.core.util

import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgPatchTest {

    private fun badge(logo: String): String {
        val inner = "<svg fill=\"#fff\" role=\"img\" viewBox=\"0 0 24 24\" xmlns=\"http://www.w3.org/2000/svg\">" +
            "<title>Logo</title><path d=\"M12 2L2 7v10l10 5 10-5V7z\"/></svg>"
        val b64 = inner.encodeUtf8().base64()
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"67\" height=\"20\" role=\"img\">" +
            "<g><rect width=\"67\" height=\"20\" fill=\"#3e74a2\"/></g>" +
            "<g fill=\"#fff\" font-size=\"110\"><image x=\"5\" y=\"3\" width=\"14\" height=\"14\" " +
            "href=\"data:image/svg+xml;base64,$b64\"/><text x=\"33\">$logo</text></g></svg>"
    }

    @Test
    fun `inline logo becomes path group with logo color`() {
        val out = patchSvgText(badge("Python"))
        assertFalse(out.contains("<image"))
        assertTrue(out.contains("<g transform=\"translate(5.0,3.0) scale(0.5833333,0.5833333)\" fill=\"#fff\">"))
        assertTrue(out.contains("<path d=\"M12 2L2 7v10l10 5 10-5V7z\"/>"))

        assertFalse(out.contains("<title>Logo</title>"))
        assertTrue(out.contains("Python"))
    }

    @Test
    fun `missing viewBox gets injected from width and height`() {
        val out = patchSvgText(badge("Go"))
        assertTrue(out.contains("viewBox=\"0 0 67 20\""))
    }

    @Test
    fun `existing viewBox is left alone`() {
        val src = "<svg viewBox=\"0 0 10 10\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>"
        assertEquals(src, patchSvgText(src))
    }

    @Test
    fun `raster images and foreign hrefs are left alone`() {

        val gif = "R0lGODlhAQABAAAAACw="
        val src = "<svg width=\"10\" height=\"10\">" +
            "<image x=\"0\" y=\"0\" width=\"4\" height=\"4\" href=\"data:image/gif;base64,$gif\"/>" +
            "<image x=\"5\" y=\"5\" width=\"4\" height=\"4\" href=\"https://example.com/a.png\"/></svg>"
        val out = patchSvgText(src)
        assertEquals(2, Regex("<image").findAll(out).count())
        assertTrue(out.contains("data:image/gif;base64,$gif"))
        assertTrue(out.contains("viewBox=\"0 0 10 10\""))
    }

    @Test
    fun `plain svg passes through untouched`() {
        val src = "<svg viewBox=\"0 0 4 4\"><circle cx=\"2\" cy=\"2\" r=\"2\"/></svg>"
        assertEquals(src, patchSvgText(src))
    }
}
