package ai.videosearch.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaUtilsTest {
    @Test
    fun `URL de video precisa ser http publico`() {
        assertThrows<DownloadException> { VideoDownloader.validate("file:///etc/passwd") }
        assertThrows<DownloadException> { VideoDownloader.validate("http://localhost:8080/x.mp4") }
        assertThrows<DownloadException> { VideoDownloader.validate("http://127.0.0.1/x.mp4") }
        assertThrows<DownloadException> { VideoDownloader.validate("http://169.254.169.254/latest/meta-data") }
        assertThrows<DownloadException> { VideoDownloader.validate("https://user:pw@www.tiktok.com/v") }
        assertThrows<DownloadException> { VideoDownloader.validate("nao é url") }
    }

    @Test
    fun `redes sociais sao reconhecidas pelo dominio e subdominio`() {
        val allowed = listOf("tiktok.com", "youtube.com", "youtu.be", "instagram.com")
        assertTrue(VideoDownloader.isSocial("www.tiktok.com", allowed))
        assertTrue(VideoDownloader.isSocial("vm.tiktok.com", allowed))
        assertTrue(VideoDownloader.isSocial("m.youtube.com", allowed))
        assertTrue(VideoDownloader.isSocial("youtu.be", allowed))
        assertFalse(VideoDownloader.isSocial("faketiktok.com", allowed))
        assertFalse(VideoDownloader.isSocial("cdn.exemplo.com", allowed))
    }

    @Test
    fun `erros do yt-dlp viram mensagens claras`() {
        assertTrue(VideoDownloader.explain("ERROR: [Instagram] abc: Requested content is not available, rate-limit reached or login required").contains("login"))
        assertTrue(VideoDownloader.explain("[download] Video does not pass filter (duration <= 900), skipping ..").contains("longo"))
        assertTrue(VideoDownloader.explain("ERROR: Unsupported URL: https://x").contains("suportado"))
    }

    @Test
    fun `transcricao do whisper vira linhas com minuto e segundo`() {
        val out = """
            whisper_init: carregando
            [00:00:00.000 --> 00:00:03.200]   Oi gente, hoje vou mostrar meu sérum.
            [00:01:05.000 --> 00:01:08.000]  É da Principia.
            [00:01:09.000 --> 00:01:10.000]
        """.trimIndent()
        assertEquals("[00:00] Oi gente, hoje vou mostrar meu sérum.\n[01:05] É da Principia.", Transcriber.formatWhisper(out))
    }

    @Test
    fun `range de bytes do player`() {
        assertEquals(0L to 99L, ai.videosearch.media.SignedMediaResource.parseRange("bytes=0-99", 1000))
        assertEquals(500L to 999L, ai.videosearch.media.SignedMediaResource.parseRange("bytes=500-", 1000))
        assertEquals(900L to 999L, ai.videosearch.media.SignedMediaResource.parseRange("bytes=-100", 1000))
        assertEquals(10L to 999L, ai.videosearch.media.SignedMediaResource.parseRange("bytes=10-5000", 1000))
        assertNull(ai.videosearch.media.SignedMediaResource.parseRange("bytes=2000-", 1000))
        assertNull(ai.videosearch.media.SignedMediaResource.parseRange("items=0-1", 1000))
        assertNull(ai.videosearch.media.SignedMediaResource.parseRange(null, 1000))
    }

    @Test
    fun `relogio e recorte`() {
        assertEquals("01:15", FrameTools.clock(75.4))
        assertEquals("1:02:03", FrameTools.clock(3723.0))
        val img = java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val jpeg = FrameTools.toJpeg(img)
        val crop = FrameTools.read(FrameTools.crop(jpeg, listOf(0, 0, 500, 500), margin = 0.0))
        assertEquals(100, crop.width)
        assertEquals(50, crop.height)
        assertEquals(jpeg, FrameTools.crop(jpeg, listOf(1, 2, 3)))
    }
}
