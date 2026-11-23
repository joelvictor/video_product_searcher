package ai.videosearch.ai

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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** IA padrão: Gemini recebe quadros e, quando enviado, o áudio na mesma chamada, com saída JSON estruturada. */
@ApplicationScoped
@Typed(GeminiProductAnalyzer::class)
class GeminiProductAnalyzer : ProductAnalyzer {
    private val log = Logger.getLogger(GeminiProductAnalyzer::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Inject lateinit var mapper: ObjectMapper

    @Inject lateinit var settings: RuntimeSettings
    private val apiKey get() = settings.get(Setting.AI_GEMINI_KEY)
    private val model get() = settings.get(Setting.AI_GEMINI_MODEL)

    @ConfigProperty(name = "vps.ai.gemini-base-url", defaultValue = "https://generativelanguage.googleapis.com")
    lateinit var baseUrl: String

    override fun analyze(input: AnalysisInput): AnalysisResult {
        val parts = mutableListOf<Map<String, Any>>(mapOf("text" to AnalysisPrompt.RULES + "\n" + AnalysisPrompt.context(input)))
        input.frames.forEachIndexed { i, f ->
            parts += mapOf("text" to "Quadro $i — ${f.label}:")
            parts += inline("image/jpeg", f.jpeg)
        }
        input.audioMp3?.let {
            parts += mapOf("text" to "Áudio do vídeo:")
            parts += inline("audio/mp3", it)
        }
        val body = mapOf(
            "contents" to listOf(mapOf("role" to "user", "parts" to parts)),
            "generationConfig" to mapOf(
                "temperature" to 0.2,
                "responseMimeType" to "application/json",
                "responseSchema" to SCHEMA,
            ),
        )
        return parseResponse(mapper, call(body))
    }

    override fun transcribe(audio: ByteArray, mimeType: String): String {
        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to "Transcreva este áudio no idioma falado. Marque o tempo de cada frase no formato [mm:ss]. Responda apenas com a transcrição."),
                        inline(mimeType.substringBefore(';'), audio),
                    ),
                ),
            ),
        )
        return call(body).path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim()
    }

    private fun inline(mime: String, bytes: ByteArray) =
        mapOf("inline_data" to mapOf("mime_type" to mime, "data" to Base64.getEncoder().encodeToString(bytes)))

    private fun call(body: Any): JsonNode {
        check(apiKey.isNotBlank()) { "IA não configurada: informe a chave do Gemini em Configurações." }
        val req = HttpRequest.newBuilder(URI("$baseUrl/v1beta/models/$model:generateContent"))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            log.warnf("Gemini -> %d %s", res.statusCode(), res.body().take(300))
            error("Gemini ${res.statusCode()}")
        }
        return mapper.readTree(res.body())
    }

    companion object {
        fun parseResponse(mapper: ObjectMapper, root: JsonNode): AnalysisResult {
            val text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("")
            if (text.isBlank()) return AnalysisResult(emptyList())
            return AnalysisParsing.fromJson(mapper, text)
        }

        private fun str(desc: String? = null, nullable: Boolean = false): Map<String, Any> =
            buildMap { put("type", "STRING"); desc?.let { put("description", it) }; if (nullable) put("nullable", true) }

        private val SCHEMA: Map<String, Any> = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "spoken_summary" to str("Resumo do que é falado sobre produtos", true),
                "screen_text" to str("Texto escrito na tela", true),
                "items" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "label" to str(),
                            "brand" to str(null, true),
                            "search_query" to str(),
                            "layer" to mapOf("type" to "STRING", "enum" to listOf("PRIMARY_FOCUS", "SCENE_ITEM")),
                            "detection_type" to mapOf("type" to "STRING", "enum" to listOf("EXACT_BRANDED_PRODUCT", "VISUAL_FASHION_ITEM")),
                            "category" to str(null, true),
                            "frame_index" to mapOf("type" to "INTEGER"),
                            "box_2d" to mapOf("type" to "ARRAY", "items" to mapOf("type" to "INTEGER")),
                            "confidence" to mapOf("type" to "NUMBER"),
                            "evidence" to mapOf(
                                "type" to "ARRAY",
                                "items" to mapOf("type" to "STRING", "enum" to listOf("AUDIO", "VISION_OCR", "SCREEN_TEXT", "CAPTION")),
                            ),
                        ),
                        "required" to listOf("label", "search_query", "layer", "detection_type", "frame_index", "box_2d", "confidence"),
                    ),
                ),
            ),
            "required" to listOf("items"),
        )
    }
}
