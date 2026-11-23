package ai.videosearch.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecretBoxTest {
    private val box = SecretBox(ByteArray(32) { it.toByte() })

    @Test
    fun `cifra e decifra, sem guardar o texto puro`() {
        val stored = box.encrypt("AIzaSy-chave-secreta")
        assertFalse(stored.contains("chave-secreta"))
        assertEquals("AIzaSy-chave-secreta", box.decrypt(stored))
        assertNotEquals(stored, box.encrypt("AIzaSy-chave-secreta"), "IV novo a cada cifra")
    }

    @Test
    fun `chave diferente ou valor adulterado nao decifra`() {
        val stored = box.encrypt("segredo")
        assertNull(SecretBox(ByteArray(32) { 9 }).decrypt(stored))
        assertNull(box.decrypt(stored.dropLast(3) + "AAA"))
        assertNull(box.decrypt("texto puro"))
    }
}
