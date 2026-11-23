package ai.videosearch.search

import ai.videosearch.settings.RuntimeSettings
import ai.videosearch.settings.Setting
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/** A busca usada pelo app: escolhe SerpAPI, Brave ou DeepSeek a cada chamada, conforme Configurações (ou `VPS_SEARCH_PROVIDER`). */
@ApplicationScoped
class ConfiguredSearch : ProductSearch {
    @Inject lateinit var settings: RuntimeSettings
    @Inject lateinit var serpapi: SerpApiProductSearch
    @Inject lateinit var brave: BraveProductSearch
    @Inject lateinit var deepseek: DeepSeekProductSearch

    private fun current(): ProductSearch = select(settings.get(Setting.SEARCH_PROVIDER), serpapi, brave, deepseek)

    override fun searchInDomain(domain: String, query: String, limit: Int) = current().searchInDomain(domain, query, limit)
    override fun visualSearch(image: VisualQuery, limit: Int) = current().visualSearch(image, limit)

    companion object {
        fun select(provider: String, serpapi: ProductSearch, brave: ProductSearch, deepseek: ProductSearch): ProductSearch =
            when (provider.trim().lowercase()) {
                "serpapi" -> serpapi
                "brave" -> brave
                "deepseek" -> deepseek
                else -> throw IllegalArgumentException("Provedor de busca inválido: '$provider' (use serpapi, brave ou deepseek)")
            }
    }
}
