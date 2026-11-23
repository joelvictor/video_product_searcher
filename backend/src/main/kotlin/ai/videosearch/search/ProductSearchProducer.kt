package ai.videosearch.search

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty

/** Escolhe a busca por `vps.search.provider` (`serpapi`, padrão, `brave` ou `deepseek`). */
class ProductSearchProducer {
    @Produces
    @ApplicationScoped
    fun search(
        @ConfigProperty(name = "vps.search.provider", defaultValue = "serpapi") provider: String,
        serpapi: SerpApiProductSearch,
        brave: BraveProductSearch,
        deepseek: DeepSeekProductSearch,
    ): ProductSearch = select(provider, serpapi, brave, deepseek)

    companion object {
        fun select(provider: String, serpapi: ProductSearch, brave: ProductSearch, deepseek: ProductSearch): ProductSearch =
            when (provider.trim().lowercase()) {
                "serpapi" -> serpapi
                "brave" -> brave
                "deepseek" -> deepseek
                else -> throw IllegalArgumentException("vps.search.provider inválido: '$provider' (use serpapi, brave ou deepseek)")
            }
    }
}
