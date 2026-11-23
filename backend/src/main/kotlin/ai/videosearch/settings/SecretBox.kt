package ai.videosearch.settings

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Cifra (AES-256-GCM) as chaves de API guardadas no banco, para que um dump do banco não as exponha. */
class SecretBox(private val key: ByteArray) {
    init { require(key.size == 32) { "chave AES precisa de 32 bytes" } }

    fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    /** Null quando o valor não foi cifrado com esta chave (por exemplo, a chave de assinatura mudou). */
    fun decrypt(stored: String): String? = runCatching {
        require(stored.startsWith(PREFIX))
        val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()

    companion object { const val PREFIX = "v1:" }
}
