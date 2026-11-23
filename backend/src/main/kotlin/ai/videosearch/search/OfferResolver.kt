package ai.videosearch.search

import ai.videosearch.affiliate.AffiliatePlatform
import ai.videosearch.affiliate.StoreCredential
import ai.videosearch.links.AffiliateLinkService
import ai.videosearch.links.GeneratedLink
import ai.videosearch.links.LinkException
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.util.UUID
import java.util.concurrent.Executors

@JsonIgnoreProperties(ignoreUnknown = true)
data class Offer(
    val platformId: String = "",
    val platformName: String = "",
    val title: String = "",
    val url: String = "",
    val imageUrl: String? = null,
    val price: String? = null,
)

class Resolution(val primary: Offer?, val alternatives: List<Offer>, val link: GeneratedLink?, val note: String? = null)

/**
 * Mesmo fluxo do StoryShop:
 * - Rota A (texto): procura o produto em cada loja habilitada (em ordem de prioridade), em paralelo,
 *   e gera o link afiliado da melhor oferta.
 * - Rota B (imagem): busca reversa pelo recorte e fica com os resultados das lojas habilitadas;
 *   sem resultado, tenta a descrição em texto.
 */
@ApplicationScoped
class OfferResolver {
    private val log = Logger.getLogger(OfferResolver::class.java)

    @Inject lateinit var search: ProductSearch
    @Inject lateinit var links: AffiliateLinkService

    private fun enabledPlatforms(): List<AffiliatePlatform> = QuarkusTransaction.requiringNew().call {
        StoreCredential.enabled().mapNotNull { c -> AffiliatePlatform.findById(c.platformId)?.takeIf { it.isActive } }
    }

    fun resolve(query: String): Resolution {
        val platforms = enabledPlatforms().filter { !it.searchDomain.isNullOrBlank() }
        if (platforms.isEmpty()) return Resolution(null, emptyList(), null, NO_STORES)

        val offers = Executors.newVirtualThreadPerTaskExecutor().use { pool ->
            platforms.map { p ->
                p to pool.submit<List<SearchHit>> {
                    runCatching { search.searchInDomain(p.searchDomain!!, query, 2) }
                        .onFailure { log.warnf("busca em %s falhou: %s", p.slug, it.message) }
                        .getOrDefault(emptyList())
                }
            }.flatMap { (p, f) -> f.get().map { hit -> offerOf(p, hit) } }
        }
        return withLink(offers, offers.drop(1))
    }

    fun resolveVisual(image: VisualQuery, fallbackQuery: String): Resolution {
        val platforms = enabledPlatforms()
        if (platforms.isEmpty()) return Resolution(null, emptyList(), null, NO_STORES)
        val hits = runCatching { search.visualSearch(image, 20) }
            .onFailure { log.warnf("busca visual falhou: %s", it.message) }
            .getOrDefault(emptyList())
        val offers = platforms.flatMap { p ->
            hits.filter { runCatching { p.matches(it.url) }.getOrDefault(false) }.map { offerOf(p, it) }
        }
        if (offers.isEmpty()) return resolve(fallbackQuery)
        return withLink(offers, offers.drop(1).take(4))
    }

    /** Gera (ou troca para) o link afiliado de uma oferta já encontrada. */
    fun linkFor(offer: Offer): GeneratedLink? {
        val platform = QuarkusTransaction.requiringNew().call { AffiliatePlatform.findById(UUID.fromString(offer.platformId)) } ?: return null
        return try {
            links.fromPlatformUrl(platform, offer.url)
        } catch (e: LinkException) {
            log.warnf("link não gerado: %s", e.message)
            null
        }
    }

    private fun withLink(offers: List<Offer>, alternatives: List<Offer>): Resolution {
        val primary = offers.firstOrNull() ?: return Resolution(null, emptyList(), null, "Nenhuma loja habilitada tem esse produto.")
        val link = linkFor(primary) ?: return Resolution(null, offers, null, "Oferta encontrada, mas faltam credenciais da loja.")
        return Resolution(primary, alternatives, link)
    }

    private fun offerOf(p: AffiliatePlatform, hit: SearchHit) =
        Offer(p.id.toString(), p.displayName, hit.title, hit.url, hit.imageUrl, hit.price)

    companion object {
        const val NO_STORES = "Nenhuma loja habilitada. Configure as lojas e credenciais em Lojas."
    }
}
