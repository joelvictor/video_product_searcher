package ai.videosearch.ai

import ai.videosearch.settings.RuntimeSettings
import ai.videosearch.settings.Setting
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class ChatEndpointTest {
    private val official = ChatEndpoint("https://api.deepseek.com", "deepseek-flash", "sk-oficial")

    @Test
    fun `monta a URL de chat a partir da base de qualquer provedor`() {
        assertEquals("https://api.deepseek.com/chat/completions", official.url.toString())
        assertEquals("https://openrouter.ai/api/v1/chat/completions", ChatEndpoint("https://openrouter.ai/api/v1/", "m", "k").url.toString())
    }

    @Test
    fun `sem chave so vale para servidor proprio`() {
        assertFalse(ChatEndpoint("https://api.deepseek.com", "m", "").configured)
        assertTrue(ChatEndpoint("http://ollama:11434/v1", "deepseek-r1", "").configured)
        assertTrue(official.configured)
    }

    @Test
    fun `busca herda da analise e nunca leva a chave para outro host`() {
        assertEquals(official, ChatEndpoint.forSearch(official, null, "", null))

        val other = ChatEndpoint.forSearch(official, "https://openrouter.ai/api/v1", null, null)
        assertEquals("deepseek-flash", other.model)
        assertEquals("", other.apiKey)

        val withKey = ChatEndpoint.forSearch(official, "https://openrouter.ai/api/v1", "deepseek/deepseek-chat", "sk-or")
        assertEquals(ChatEndpoint("https://openrouter.ai/api/v1", "deepseek/deepseek-chat", "sk-or"), withKey)

        val viaProxy = ChatEndpoint("https://openrouter.ai/api/v1", "deepseek/deepseek-chat", "sk-or")
        assertEquals("", ChatEndpoint.forSearch(viaProxy, "https://api.deepseek.com", "deepseek-flash", null).apiKey)
    }

    @Test
    fun `toString nao mostra a chave`() {
        assertFalse(official.toString().contains("sk-oficial"))
    }

    @Test
    fun `chama o provedor configurado com modelo e chave`() {
        val seen = mutableListOf<Triple<String, String?, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seen += Triple(ex.requestURI.path, ex.requestHeaders.getFirst("Authorization"), ex.requestBody.readAllBytes().decodeToString())
            val out = """{"choices":[{"message":{"content":"{}"}}]}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}/api/v1"
            val values = mapOf(
                Setting.AI_DEEPSEEK_KEY to "sk-teste",
                Setting.AI_DEEPSEEK_MODEL to "deepseek/deepseek-chat",
                Setting.AI_DEEPSEEK_BASE_URL to base,
            )
            val client = DeepSeekClient().apply {
                mapper = ObjectMapper()
                settings = object : RuntimeSettings() { override fun get(s: Setting) = values[s] ?: "" }
            }
            client.chat(client.analysis, mapOf("messages" to emptyList<Any>()))
            client.chat(ChatEndpoint(base, "local", ""), mapOf("messages" to emptyList<Any>()))

            assertEquals("/api/v1/chat/completions", seen[0].first)
            assertEquals("Bearer sk-teste", seen[0].second)
            assertEquals("deepseek/deepseek-chat", ObjectMapper().readTree(seen[0].third)["model"].asText())
            assertNull(seen[1].second)
            assertEquals("local", ObjectMapper().readTree(seen[1].third)["model"].asText())
        } finally {
            server.stop(0)
        }
    }
}
