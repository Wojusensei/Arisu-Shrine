package dev.arisu.shrine.feature.share

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 六个签等都有原创签文与宜忌，二维码矩阵能生成。 */
class FortuneCardContentTest {

    private val bands = listOf("大吉", "吉", "中吉", "小吉", "末吉", "凶")

    @Test
    fun everyBandHasVerseAndYiJi() {
        val all = bands.map { band ->
            val text = fortuneText(band)
            assertEquals(2, text.verse.size)
            assertTrue(text.verse.all { it.isNotBlank() })
            assertTrue(text.yi.isNotBlank())
            assertTrue(text.ji.isNotBlank())
            text
        }
        assertEquals(bands.size, all.map { it.verse }.toSet().size)
        assertEquals(bands.size, all.map { it.yi to it.ji }.toSet().size)
    }

    @Test
    fun unknownBandFallsBackToBlank() {
        val text = fortuneText("未知")
        assertTrue(text.verse.isEmpty())
        assertEquals("", text.yi)
        assertEquals("", text.ji)
    }

    @Test
    fun qrMatrixIsSquare() {
        val matrix = QrCode.matrix("https://arisushrine.com")
        assertTrue(matrix != null)
        assertEquals(matrix!!.width, matrix.height)
        assertTrue(matrix.width > 0)
    }

    @Test
    fun qrMatrixDecodesBackToSite() {
        val matrix = QrCode.matrix("https://arisushrine.com")!!
        // 卡片上每个模块约 5px；按 4px 放大后再解码，和实际出图同一量级
        val bitmap = BinaryBitmap(HybridBinarizer(MatrixSource(matrix, quiet = 4, scale = 4)))
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        assertEquals("https://arisushrine.com", QRCodeReader().decode(bitmap, hints).text)
    }

    @Test
    fun invertedQrAlsoDecodes() {
        val matrix = QrCode.matrix("https://arisushrine.com")!!
        val bitmap = BinaryBitmap(
            HybridBinarizer(MatrixSource(matrix, quiet = 4, scale = 4, inverted = true)),
        )
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.ALSO_INVERTED to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        assertEquals("https://arisushrine.com", MultiFormatReader().decode(bitmap, hints).text)
    }

    /** 把生成的二维码矩阵补上静区并按模块放大，直接喂给解码器。 */
    private class MatrixSource(
        private val matrix: BitMatrix,
        quiet: Int,
        private val scale: Int,
        inverted: Boolean = false,
    ) : LuminanceSource(
        (matrix.width + quiet * 2) * scale,
        (matrix.height + quiet * 2) * scale,
    ) {

        private val on = if (inverted) 255.toByte() else 0.toByte()
        private val off = if (inverted) 0.toByte() else 255.toByte()

        private val pixels = ByteArray(width * height) { index ->
            val x = index % width / scale - quiet
            val y = index / width / scale - quiet
            val dark = x in 0 until matrix.width && y in 0 until matrix.height && matrix.get(x, y)
            if (dark) on else off
        }

        override fun getRow(y: Int, row: ByteArray): ByteArray {
            System.arraycopy(pixels, y * width, row, 0, width)
            return row
        }

        override fun getMatrix(): ByteArray = pixels
    }
}
