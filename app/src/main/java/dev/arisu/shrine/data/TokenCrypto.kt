package dev.arisu.shrine.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 令牌静态加密：AndroidKeyStore 内的 AES/GCM 密钥，密钥材料不出安全硬件。
 * 密文格式 `enc:v1:<base64(iv||ciphertext)>`。
 * - 读迁移：无前缀的存量明文原样透传，下次落盘自然变成密文
 * - fail-closed：解密失败（密钥被清/密文被改/换机还原）返回 null，调用方必须按
 *   「无令牌」处理——宁可重新登录，也不能把来历不明的密文当真
 */
object TokenCrypto {

    private const val PREFIX = "enc:v1:"
    private const val ALIAS = "arisu_token_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /** Keystore 不可用（极旧设备/厂商裁剪）时回落明文：不把令牌丢掉，行为等同加固前 */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
        } catch (_: Throwable) {
            plain
        }
    }

    /** 返回 null 表示解不出可信令牌，调用方按未登录处理 */
    fun decrypt(stored: String): String? {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val sealed = Base64.decode(stored.removePrefix(PREFIX), Base64.DEFAULT)
            if (sealed.size <= IV_LEN) return null
            val spec = GCMParameterSpec(TAG_BITS, sealed, 0, IV_LEN)
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), spec) }
            String(cipher.doFinal(sealed, IV_LEN, sealed.size - IV_LEN), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }
}
