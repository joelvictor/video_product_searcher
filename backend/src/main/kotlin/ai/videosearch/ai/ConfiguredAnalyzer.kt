package ai.videosearch.ai

import ai.videosearch.settings.RuntimeSettings
import ai.videosearch.settings.Setting
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/** A IA usada pelo app: escolhe Gemini ou DeepSeek a cada chamada, conforme Configurações (ou `VPS_AI_PROVIDER`). */
@ApplicationScoped
class ConfiguredAnalyzer : ProductAnalyzer {
    @Inject lateinit var settings: RuntimeSettings
    @Inject lateinit var gemini: GeminiProductAnalyzer
    @Inject lateinit var deepseek: DeepSeekProductAnalyzer

    private fun current(): ProductAnalyzer = select(settings.get(Setting.AI_PROVIDER), gemini, deepseek)

    override fun analyze(input: AnalysisInput) = current().analyze(input)
    override fun transcribe(audio: ByteArray, mimeType: String) = current().transcribe(audio, mimeType)

    companion object {
        fun select(provider: String, gemini: ProductAnalyzer, deepseek: ProductAnalyzer): ProductAnalyzer =
            when (provider.trim().lowercase()) {
                "gemini" -> gemini
                "deepseek" -> deepseek
                else -> throw IllegalArgumentException("Provedor de IA inválido: '$provider' (use gemini ou deepseek)")
            }
    }
}
