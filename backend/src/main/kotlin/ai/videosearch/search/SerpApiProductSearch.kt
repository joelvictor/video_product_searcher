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

/** Google (texto por domínio) e Google Lens (imagem por URL pública) via SerpAPI. */
@ApplicationScoped
@Typed(SerpApiProductSearch::class)
class SerpApiProductSearch : ProductSearch {
    private val log = Logger.getLogger(SerpApiProductSearch::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Inject lateinit var mapper: ObjectMapper

    @Inject lateinit var settings: RuntimeSettings
    private val apiKey get() = settings.get(Setting.SEARCH_SERPAPI_KEY)

    @ConfigProperty(name = "vps.search.serpapi-base-url", defaultValue = "https://serpapi.com")
    lateinit var baseUrl: String

    override fun searchInDomain(domain: String, query: String, limit: Int): List<SearchHit> {
        if (apiKey.isBlank()) return emptyList()
        val json = get("engine=google&gl=br&hl=pt-br&num=10&q=${enc("site:$domain $query")}")
        return json.path("organic_results").mapNotNull { r ->
            val link = r.path("link").asText(null) ?: return@mapNotNull null
            if (!hostMatchesDomain(hostOf(link), domain)) return@mapNotNull null
            SearchHit(
                title = r.path("title").asText(""),
                url = link,
                imageUrl = r.path("thumbnail").asText(null),
                price = r.path("rich_snippet").path("bottom").path("detected_extensions").path("price").asText(null)
                    ?: r.path("rich_snippet").path("top").path("detected_extensions").path("price").asText(null)?.let { "R$ $it" }
                    ?: findPrice(r.path("snippet").asText(null)),
            )
        }.take(limit)
    }

    override fun visualSearch(image: VisualQuery, limit: Int): List<SearchHit> {
        if (apiKey.isBlank()) return emptyList()
        val json = get("engine=google_lens&hl=pt-br&country=br&url=${enc(image.imageUrl)}")
        return json.path("visual_matches").mapNotNull { r ->
            val link = r.path("link").asText(null) ?: return@mapNotNull null
            SearchHit(
                title = r.path("title").asText(""),
                url = link,
                imageUrl = r.path("thumbnail").asText(null),
                price = r.path("price").path("value").asText(null),
            )
        }.take(limit)
    }

    private fun get(query: String): JsonNode {
        val req = HttpRequest.newBuilder(URI("$baseUrl/search.json?$query&api_key=${enc(apiKey)}"))
            .timeout(Duration.ofSeconds(25)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            log.warnf("SerpAPI -> %d", res.statusCode())
            error("SerpAPI ${res.statusCode()}")
        }
        return mapper.readTree(res.body())
    }

    private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)
}
