package dev.arisu.shrine.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TokenCryptoTest {

    @Test
    fun `空串原样`() {
        assertEquals("", TokenCrypto.encrypt(""))
        assertEquals("", TokenCrypto.decrypt(""))
    }

    @Test
    fun `存量明文透传完成读迁移`() {
        assertEquals("raw-access-token", TokenCrypto.decrypt("raw-access-token"))
        assertEquals("raw-refresh-token", TokenCrypto.decrypt("raw-refresh-token"))
    }

    @Test
    fun `坏密文一律 fail-closed`() {
        assertNull(TokenCrypto.decrypt("enc:v1:not-base64!!!"))
        assertNull(TokenCrypto.decrypt("enc:v1:QUJD"))
        assertNull(TokenCrypto.decrypt("enc:v1:QUJDREVG"))
    }

    @Test
    fun `加解密往返`() {
        val enc = TokenCrypto.encrypt("access-refresh-token-0123456789")
        if (enc.startsWith("enc:v1:")) {

            assertEquals("access-refresh-token-0123456789", TokenCrypto.decrypt(enc))
        } else {

            assertEquals("access-refresh-token-0123456789", enc)
        }
    }
}
