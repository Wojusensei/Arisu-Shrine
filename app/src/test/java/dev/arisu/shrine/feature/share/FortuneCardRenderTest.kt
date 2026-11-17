package dev.arisu.shrine.feature.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 三种风格都能落成有效位图，底色取各自纸色。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FortuneCardRenderTest {

    @Test
    fun everyStyleRendersWithItsPaperColor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FortuneCardStyle.entries.forEach { style ->
            val data = FortuneCardData(
                luck = 59,
                band = "中吉",
                date = "2026.11.10",
                userName = "甲",
                userNo = "User_000001",
                options = FortuneCardOptions(showQr = true),
                style = style,
            )
            val bitmap = FortuneCard.render(context, data, width = 216)
            try {
                assertEquals(216, bitmap.width)
                assertEquals(288, bitmap.height)
                assertFalse(bitmap.isRecycled)
                assertEquals(paletteFor(style).paper, bitmap.getPixel(0, 0))
            } finally {
                bitmap.recycle()
            }
        }
    }
}
