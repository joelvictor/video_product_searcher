package ai.videosearch.media

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DirectDownloadTest {
    private lateinit var server: HttpServer
    private val release = CountDownLatch(1)

    private val downloader = VideoDownloader().apply {
        allowedDomains = listOf("tiktok.com")
        maxBytes = 1_000
        timeout = Duration.ofSeconds(2)
    }

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/ok.mp4") { ex ->
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { it.write(ByteArray(500) { 7 }) }
        }
        server.createContext("/grande.mp4") { ex ->
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.sendResponseHeaders(200, 0) // chunked, sem content-length
            ex.responseBody.use { out -> repeat(10) { out.write(ByteArray(500)) } }
        }
        server.createContext("/travado.mp4") { ex ->
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.write(ByteArray(10))
            ex.responseBody.flush()
            release.await() // segura o corpo aberto até o fim do teste
            ex.close()
        }
        server.createContext("/pagina") { ex ->
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { it.write("<html></html>".toByteArray()) }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        release.countDown()
        server.stop(0)
    }

    private fun url(path: String) = URI("http://127.0.0.1:${server.address.port}$path")

    @Test
    fun `baixa o corpo inteiro de um link direto`() {
        val video = downloader.direct(url("/ok.mp4"))
        assertArrayEquals(ByteArray(500) { 7 }, Files.readAllBytes(video.file))
        Files.delete(video.file)
    }

    @Test
    fun `corta o download que passa do limite sem content-length`() {
        assertThrows<DownloadException> { downloader.direct(url("/grande.mp4")) }
    }

    @Test
    fun `recusa link que nao e video`() {
        assertThrows<DownloadException> { downloader.direct(url("/pagina")) }
    }

    @Test
    fun `corpo travado respeita o prazo e nao segura o worker`() {
        val began = System.nanoTime()
        val e = assertThrows<DownloadException> { downloader.direct(url("/travado.mp4")) }
        val elapsed = Duration.ofNanos(System.nanoTime() - began)
        assertTrue(elapsed < Duration.ofSeconds(10), "levou $elapsed")
        assertTrue(e.message!!.contains("demorou"))
    }
}
