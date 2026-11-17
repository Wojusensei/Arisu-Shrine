package dev.arisu.shrine.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 令牌静态加密。Robolectric 没有 Keystore：encrypt 回落明文、decrypt 对密文一律拒绝，
 * 这两个分支本身就是安全语义，必须在此锁死——明文透传（读迁移）、坏密文 fail-closed。
 */
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
        assertNull(TokenCrypto.decrypt("enc:v1:QUJD")) // 合法 base64 但不足一条 GCM 密文
        assertNull(TokenCrypto.decrypt("enc:v1:QUJDREVG"))
    }

    @Test
    fun `加解密往返`() {
        val enc = TokenCrypto.encrypt("access-refresh-token-0123456789")
        if (enc.startsWith("enc:v1:")) {
            // Keystore 可用的环境：往返一致
            assertEquals("access-refresh-token-0123456789", TokenCrypto.decrypt(enc))
        } else {
            // Keystore 不可用：回落明文，行为等同加固前
            assertEquals("access-refresh-token-0123456789", enc)
        }
    }
}
