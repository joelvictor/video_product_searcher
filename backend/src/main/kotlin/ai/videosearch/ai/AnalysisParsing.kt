package ai.videosearch.ai

import com.fasterxml.jackson.databind.ObjectMapper

/** Converte o JSON de itens (mesmo formato em todos os provedores de IA) em [AnalysisResult]. */
object AnalysisParsing {
    fun fromJson(mapper: ObjectMapper, text: String): AnalysisResult {
        val node = mapper.readTree(stripFences(text))
        val items = node.path("items").map { n ->
            DetectedProduct(
                label = n.path("label").asText(""),
                brand = n.path("brand").takeIf { !it.isMissingNode && !it.isNull }?.asText()?.ifBlank { null },
                searchQuery = n.path("search_query").asText(n.path("label").asText("")),
                layer = n.path("layer").asText("PRIMARY_FOCUS"),
                detectionType = n.path("detection_type").asText("EXACT_BRANDED_PRODUCT"),
                category = n.path("category").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
                frameIndex = n.path("frame_index").asInt(0),
                box = n.path("box_2d").map { it.asInt() },
                confidence = n.path("confidence").asDouble(0.0),
                evidence = n.path("evidence").map { it.asText() },
            )
        }.filter { it.label.isNotBlank() }
        return AnalysisResult(
            items,
            node.path("spoken_summary").takeUnless { it.isNull }?.asText(null),
            node.path("screen_text").takeUnless { it.isNull }?.asText(null),
        )
    }

    /** Alguns modelos devolvem o JSON dentro de ```json ... ```. */
    fun stripFences(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        return t.substringAfter('\n').substringBeforeLast("```").trim()
    }
}
