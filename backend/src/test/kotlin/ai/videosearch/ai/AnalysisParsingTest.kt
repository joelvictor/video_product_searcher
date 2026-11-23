package ai.videosearch.ai

import ai.videosearch.search.ProductSearch
import ai.videosearch.search.ConfiguredSearch
import ai.videosearch.search.SearchHit
import ai.videosearch.search.VisualQuery
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AnalysisParsingTest {
    private val mapper = ObjectMapper()

    private val itemsJson = """{"spoken_summary":"fala do sérum","screen_text":null,"items":[
        {"label":"Sérum Vitamina C","brand":"Principia","search_query":"serum vitamina c principia","layer":"PRIMARY_FOCUS",
         "detection_type":"EXACT_BRANDED_PRODUCT","category":"beleza","frame_index":2,"box_2d":[100,200,500,600],"confidence":0.91,"evidence":["AUDIO","VISION_OCR"]},
        {"label":"","search_query":"x","layer":"SCENE_ITEM","detection_type":"VISUAL_FASHION_ITEM","frame_index":0,"box_2d":[],"confidence":0.3}]}"""

    @Test
    fun `Gemini converte a resposta em itens e ignora item sem nome`() {
        val root = mapper.readTree(mapper.writeValueAsString(mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to itemsJson))))))))
        val r = GeminiProductAnalyzer.parseResponse(mapper, root)
        assertEquals(1, r.items.size)
        assertEquals("Principia", r.items[0].brand)
        assertEquals(2, r.items[0].frameIndex)
        assertEquals(listOf(100, 200, 500, 600), r.items[0].box)
        assertEquals("fala do sérum", r.spokenSummary)
        assertNull(r.screenText)
    }

    @Test
    fun `DeepSeek converte a resposta do chat, mesmo com bloco de codigo`() {
        val fenced = "```json\n$itemsJson\n```"
        val root = mapper.readTree(mapper.writeValueAsString(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to fenced))))))
        val r = DeepSeekProductAnalyzer.parseResponse(mapper, root)
        assertEquals(listOf("Sérum Vitamina C"), r.items.map { it.label })
    }

    @Test
    fun `resposta vazia nao quebra`() {
        assertEquals(0, DeepSeekProductAnalyzer.parseResponse(mapper, mapper.readTree("{}")).items.size)
        assertEquals(0, GeminiProductAnalyzer.parseResponse(mapper, mapper.readTree("{}")).items.size)
    }

    @Test
    fun `contexto do prompt leva transcricao, legenda e aviso do modo manual`() {
        val ctx = AnalysisPrompt.context(AnalysisInput(emptyList(), transcript = "[00:05] esse tênis é da Nike", caption = "meu look", userSelected = true))
        assert(ctx.contains("esse tênis é da Nike"))
        assert(ctx.contains("meu look"))
        assert(ctx.contains("escolhidos pelo usuário"))
    }

    private class StubAnalyzer : ProductAnalyzer {
        override fun analyze(input: AnalysisInput) = AnalysisResult(emptyList())
        override fun transcribe(audio: ByteArray, mimeType: String) = ""
    }

    private class StubSearch : ProductSearch {
        override fun searchInDomain(domain: String, query: String, limit: Int) = emptyList<SearchHit>()
        override fun visualSearch(image: VisualQuery, limit: Int) = emptyList<SearchHit>()
    }

    @Test
    fun `provedores sao escolhidos por configuracao`() {
        val g = StubAnalyzer(); val d = StubAnalyzer()
        assertSame(g, ConfiguredAnalyzer.select("gemini", g, d))
        assertSame(d, ConfiguredAnalyzer.select(" DeepSeek ", g, d))
        assertThrows(IllegalArgumentException::class.java) { ConfiguredAnalyzer.select("xyz", g, d) }

        val s = StubSearch(); val b = StubSearch(); val ds = StubSearch()
        assertSame(s, ConfiguredSearch.select("serpapi", s, b, ds))
        assertSame(b, ConfiguredSearch.select("Brave", s, b, ds))
        assertSame(ds, ConfiguredSearch.select("deepseek", s, b, ds))
        assertThrows(IllegalArgumentException::class.java) { ConfiguredSearch.select("bing", s, b, ds) }
    }
}
