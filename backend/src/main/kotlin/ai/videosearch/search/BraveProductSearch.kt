package ai.videosearch.search

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Typed
import jakarta.inject.Inject
import ai.videosearch.settings.RuntimeSettings
import ai.videosearch.settings.Setting
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Busca de texto pelo Brave Search API (`site:{domínio} {consulta}`). O Brave não tem busca reversa por imagem,
 * então [visualSearch] devolve vazio e o [OfferResolver] cai na busca por texto com a descrição do item.
 */
@ApplicationScoped
@Typed(BraveProductSearch::class)
class BraveProductSearch : ProductSearch {
    private val log = Logger.getLogger(BraveProductSearch::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Inject lateinit var mapper: ObjectMapper

    @Inject lateinit var settings: RuntimeSettings
    private val apiKey get() = settings.get(Setting.SEARCH_BRAVE_KEY)

    @ConfigProperty(name = "vps.search.brave-base-url", defaultValue = "https://api.search.brave.com")
    lateinit var baseUrl: String

    override fun searchInDomain(domain: String, query: String, limit: Int): List<SearchHit> {
        if (apiKey.isBlank()) return emptyList()
        val json = get("q=${enc("site:$domain $query")}&country=br&search_lang=pt&count=20")
        return parseResults(json, domain, limit)
    }

    override fun visualSearch(image: VisualQuery, limit: Int): List<SearchHit> = emptyList()

    private fun get(query: String): JsonNode {
        val req = HttpRequest.newBuilder(URI("$baseUrl/res/v1/web/search?$query"))
            .header("Accept", "application/json")
            .header("X-Subscription-Token", apiKey)
            .timeout(Duration.ofSeconds(20)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            log.warnf("Brave Search -> %d", res.statusCode())
            error("Brave Search ${res.statusCode()}")
        }
        return mapper.readTree(res.body())
    }

    private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)

    companion object {
        fun parseResults(json: JsonNode, domain: String, limit: Int): List<SearchHit> =
            json.path("web").path("results").mapNotNull { r ->
                val link = r.path("url").asText(null) ?: return@mapNotNull null
                if (!hostMatchesDomain(hostOf(link), domain)) return@mapNotNull null
                SearchHit(
                    title = r.path("title").asText(""),
                    url = link,
                    imageUrl = r.path("thumbnail").path("src").asText(null),
                    price = findPrice(r.path("description").asText(null)),
                )
            }.take(limit)
    }
}
