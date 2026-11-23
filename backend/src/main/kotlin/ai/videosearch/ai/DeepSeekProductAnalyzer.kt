package ai.videosearch.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Typed
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.util.Base64

/**
 * Provedor alternativo de IA. O `deepseek-flash` recebe imagens (quadros em data URL base64), mas a API não aceita áudio:
 * a fala entra como texto, pela transcrição em CPU (whisper.cpp), quando configurada.
 */
@ApplicationScoped
@Typed(DeepSeekProductAnalyzer::class)
class DeepSeekProductAnalyzer : ProductAnalyzer {
    private val log = Logger.getLogger(DeepSeekProductAnalyzer::class.java)

    @Inject lateinit var mapper: ObjectMapper
    @Inject lateinit var client: DeepSeekClient

    override fun analyze(input: AnalysisInput): AnalysisResult {
        val context = AnalysisPrompt.context(input)
        if (input.frames.isEmpty() && context.isBlank()) {
            log.info("DeepSeek: sem quadros nem texto, nada a analisar")
            return AnalysisResult(emptyList())
        }
        val content = mutableListOf<Map<String, Any>>(mapOf("type" to "text", "text" to context.ifBlank { "Analise os quadros." }))
        input.frames.forEachIndexed { i, f ->
            content += mapOf("type" to "text", "text" to "Quadro $i — ${f.label}:")
            content += mapOf(
                "type" to "image_url",
                "image_url" to mapOf("url" to "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(f.jpeg)}"),
            )
        }
        val body = mapOf(
            "temperature" to 0.2,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to AnalysisPrompt.RULES + "\nNão há áudio; a fala, quando existir, vem transcrita.\n" + AnalysisPrompt.JSON_FORMAT),
                mapOf("role" to "user", "content" to content),
            ),
        )
        return parseResponse(mapper, client.chat(client.analysis, body))
    }

    /** A API do DeepSeek não transcreve áudio; a transcrição usa o whisper.cpp. */
    override fun transcribe(audio: ByteArray, mimeType: String): String =
        throw UnsupportedOperationException("DeepSeek não transcreve áudio")

    companion object {
        fun parseResponse(mapper: ObjectMapper, root: JsonNode): AnalysisResult {
            val text = root.path("choices").path(0).path("message").path("content").asText("")
            if (text.isBlank()) return AnalysisResult(emptyList())
            return AnalysisParsing.fromJson(mapper, text)
        }
    }
}
