package ai.videosearch.ai

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty

/** Escolhe a IA por `vps.ai.provider` (`gemini`, padrão, ou `deepseek`). */
class ProductAnalyzerProducer {
    @Produces
    @ApplicationScoped
    fun analyzer(
        @ConfigProperty(name = "vps.ai.provider", defaultValue = "gemini") provider: String,
        gemini: GeminiProductAnalyzer,
        deepseek: DeepSeekProductAnalyzer,
    ): ProductAnalyzer = select(provider, gemini, deepseek)

    companion object {
        fun select(provider: String, gemini: ProductAnalyzer, deepseek: ProductAnalyzer): ProductAnalyzer =
            when (provider.trim().lowercase()) {
                "gemini" -> gemini
                "deepseek" -> deepseek
                else -> throw IllegalArgumentException("vps.ai.provider inválido: '$provider' (use gemini ou deepseek)")
            }
    }
}
