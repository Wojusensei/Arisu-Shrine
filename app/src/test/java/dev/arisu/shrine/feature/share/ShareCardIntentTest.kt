package dev.arisu.shrine.feature.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardIntentTest {

    private val uri = Uri.parse("content://dev.arisu.shrine.debug.fileprovider/share/card.png")

    @Test
    fun chooserGrantsPreviewUri() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = ShareCard.chooserWithPreviewGrant(send, "分享御签")

        assertEquals(uri, chooser.clipData?.getItemAt(0)?.uri)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(send, chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
    }
}
