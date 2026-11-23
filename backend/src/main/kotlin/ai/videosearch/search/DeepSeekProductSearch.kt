package ai.videosearch.search

import ai.videosearch.ai.DeepSeekClient
import com.fasterxml.jackson.databind.JsonNode
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Typed
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.util.Base64

/**
 * Busca pela ferramenta de servidor `deepseek_web_search` do DeepSeek (texto e imagem; a API não cobra por busca, só tokens).
 * A ferramenta não aceita filtro de domínio: a loja vai no pedido e os resultados são filtrados aqui pelo host,
 * do mesmo jeito que nos outros provedores. Só usamos as fontes que a ferramenta devolve, nunca URLs escritas pelo modelo.
 * Referência: https://api-docs.deepseek.com/guides/web_tools
 */
@ApplicationScoped
@Typed(DeepSeekProductSearch::class)
class DeepSeekProductSearch : ProductSearch {
    private val log = Logger.getLogger(DeepSeekProductSearch::class.java)

    @Inject lateinit var client: DeepSeekClient

    override fun searchInDomain(domain: String, query: String, limit: Int): List<SearchHit> {
        if (!client.configured) return emptyList()
        val prompt = "Pesquise na loja online $domain (site:$domain) a página de compra deste produto: $query. " +
            "Quero páginas de produto do Brasil nesse domínio, não artigos ou resenhas."
        val root = client.chat(request(listOf(mapOf("type" to "text", "text" to prompt))))
        return parseSources(root).filter { hostMatchesDomain(hostOf(it.url), domain) }.take(limit)
    }

    override fun visualSearch(image: VisualQuery, limit: Int): List<SearchHit> {
        if (!client.configured) return emptyList()
        val content = listOf(
            mapOf("type" to "text", "text" to "Encontre este produto à venda em lojas online do Brasil. Procure pela imagem e me diga onde comprar."),
            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(image.jpeg)}")),
        )
        return parseSources(client.chat(request(content))).take(limit)
    }

    private fun request(content: List<Map<String, Any>>) = mapOf(
        "model" to client.model,
        "thinking" to mapOf("type" to "disabled"),
        "tools" to listOf(mapOf("type" to "deepseek_web_search")),
        "messages" to listOf(mapOf("role" to "user", "content" to content)),
    )

    companion object {
        /**
         * Junta as fontes de `message.server_tool_calls[].sources` e as citações `message.annotations` (url_citation),
         * sem repetir URL. `image_url` (busca por imagem) vira a miniatura e o preço vem do snippet quando houver.
         */
        fun parseSources(root: JsonNode): List<SearchHit> {
            val message = root.path("choices").path(0).path("message")
            val hits = LinkedHashMap<String, SearchHit>()
            for (call in message.path("server_tool_calls")) {
                for (s in call.path("sources")) {
                    val url = s.path("url").asText(null)?.takeIf { it.startsWith("http") } ?: continue
                    hits.putIfAbsent(
                        url,
                        SearchHit(
                            title = s.path("title").asText("").ifBlank { s.path("site_name").asText("") },
                            url = url,
                            imageUrl = s.path("image_url").asText(null)?.takeIf { it.startsWith("http") },
                            price = findPrice(s.path("snippet").asText(null)),
                        ),
                    )
                }
            }
            for (a in message.path("annotations")) {
                val c = if (a.has("url_citation")) a.path("url_citation") else a
                val url = c.path("url").asText(null)?.takeIf { it.startsWith("http") } ?: continue
                hits.putIfAbsent(url, SearchHit(title = c.path("title").asText(url), url = url))
            }
            return hits.values.toList()
        }
    }
}
