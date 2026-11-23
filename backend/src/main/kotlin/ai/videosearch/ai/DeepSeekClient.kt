package ai.videosearch.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ai.videosearch.settings.RuntimeSettings
import ai.videosearch.settings.Setting
import org.jboss.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Um endpoint `/chat/completions` no formato da OpenAI: a API oficial do DeepSeek ou qualquer provedor que sirva
 * o modelo pela mesma interface (OpenRouter, Together, Fireworks, Groq, Azure, vLLM, Ollama...).
 * [baseUrl] é tudo o que vem antes de `/chat/completions`.
 */
data class ChatEndpoint(val baseUrl: String, val model: String, val apiKey: String) {
    val url: URI get() = URI("${baseUrl.trim().trimEnd('/')}/chat/completions")

    /** Com chave, ou sem chave num servidor próprio (vLLM, Ollama) que não a exige. */
    val configured get() = apiKey.isNotBlank() || !isOfficial

    val isOfficial get() = sameHost(baseUrl, DEEPSEEK_BASE_URL)

    override fun toString() = "ChatEndpoint($url, $model)" // nunca imprime a chave

    companion object {
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"

        fun sameHost(a: String, b: String): Boolean =
            runCatching { URI(a.trim()).host.equals(URI(b.trim()).host, ignoreCase = true) }.getOrDefault(false)

        /**
         * A busca pode usar outro provedor que a análise. Os campos vazios herdam da análise, mas a chave só é
         * herdada quando a busca vai para o mesmo host: a chave de um provedor nunca é enviada a outro.
         */
        fun forSearch(analysis: ChatEndpoint, baseUrl: String?, model: String?, apiKey: String?): ChatEndpoint {
            val base = baseUrl?.takeIf { it.isNotBlank() } ?: analysis.baseUrl
            val key = apiKey?.takeIf { it.isNotBlank() } ?: if (sameHost(base, analysis.baseUrl)) analysis.apiKey else ""
            return ChatEndpoint(base, model?.takeIf { it.isNotBlank() } ?: analysis.model, key)
        }
    }
}

/** Chamada a `/chat/completions`, compartilhada pela análise de quadros e pela busca na web. */
@ApplicationScoped
class DeepSeekClient {
    private val log = Logger.getLogger(DeepSeekClient::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Inject lateinit var mapper: ObjectMapper

    @Inject lateinit var settings: RuntimeSettings

    /** Endpoint da análise de quadros. */
    val analysis get() = ChatEndpoint(
        settings.get(Setting.AI_DEEPSEEK_BASE_URL), settings.get(Setting.AI_DEEPSEEK_MODEL), settings.get(Setting.AI_DEEPSEEK_KEY),
    )

    /** Endpoint da busca na web, herdando da análise o que não for definido. */
    val search get() = ChatEndpoint.forSearch(
        analysis, settings.get(Setting.SEARCH_DEEPSEEK_BASE_URL), settings.get(Setting.SEARCH_DEEPSEEK_MODEL), settings.get(Setting.SEARCH_DEEPSEEK_KEY),
    )

    fun chat(endpoint: ChatEndpoint, body: Map<String, Any>, timeout: Duration = Duration.ofSeconds(120)): JsonNode {
        check(endpoint.configured) { "IA não configurada: informe a chave do DeepSeek em Configurações." }
        val req = HttpRequest.newBuilder(endpoint.url)
            .header("Content-Type", "application/json")
            .apply { if (endpoint.apiKey.isNotBlank()) header("Authorization", "Bearer ${endpoint.apiKey}") }
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf("model" to endpoint.model) + body))).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            log.warnf("%s -> %d %s", endpoint.url.host, res.statusCode(), res.body().take(300))
            error("${endpoint.url.host} respondeu ${res.statusCode()}")
        }
        return mapper.readTree(res.body())
    }
}
