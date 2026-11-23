package ai.videosearch.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Chamada a `/chat/completions` do DeepSeek, compartilhada pela análise de quadros e pela busca na web. */
@ApplicationScoped
class DeepSeekClient {
    private val log = Logger.getLogger(DeepSeekClient::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Inject lateinit var mapper: ObjectMapper

    @ConfigProperty(name = "vps.ai.deepseek-api-key")
    lateinit var apiKeyOpt: java.util.Optional<String>
    private val apiKey get() = apiKeyOpt.orElse("")

    @ConfigProperty(name = "vps.ai.deepseek-model", defaultValue = "deepseek-flash")
    lateinit var model: String

    @ConfigProperty(name = "vps.ai.deepseek-base-url", defaultValue = "https://api.deepseek.com")
    lateinit var baseUrl: String

    val configured get() = apiKey.isNotBlank()

    fun chat(body: Any, timeout: Duration = Duration.ofSeconds(120)): JsonNode {
        check(configured) { "DeepSeek não configurado (VPS_AI_DEEPSEEK_API_KEY)" }
        val req = HttpRequest.newBuilder(URI("${baseUrl.trimEnd('/')}/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            log.warnf("DeepSeek -> %d %s", res.statusCode(), res.body().take(300))
            error("DeepSeek ${res.statusCode()}")
        }
        return mapper.readTree(res.body())
    }
}
