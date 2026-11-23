package ai.videosearch.pipeline

import ai.videosearch.ai.DetectedProduct
import ai.videosearch.search.SearchHit
import ai.videosearch.support.FakeAnalyzer
import ai.videosearch.support.FakeSearch
import ai.videosearch.support.ffmpegAvailable
import ai.videosearch.support.testVideo
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.path.json.JsonPath
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID

@QuarkusTest
class SearchFlowTest {
    @Inject lateinit var pipeline: SearchPipeline
    @Inject lateinit var analyzer: FakeAnalyzer
    @Inject lateinit var search: FakeSearch

    private val auth = "Bearer senha-de-teste"

    private fun api() = given().header("Authorization", auth)

    @BeforeEach
    fun stores() {
        analyzer.calls.clear()
        search.results.clear()
        search.visualCalls.clear()
        search.visualResults = emptyList()
        val amazon = api().get("/api/stores").jsonPath().getList<Map<String, Any>>("").first { it["slug"] == "amazon-br" }["id"]
        api().contentType(ContentType.JSON).body(mapOf("values" to mapOf("amazon_tag" to "conta-20"), "enabled" to true, "priority" to 1))
            .put("/api/stores/$amazon/credential").then().statusCode(200).body("configured", equalTo(true))
    }

    private fun upload(mode: String): UUID {
        val video = testVideo(6)
        try {
            val id = api().multiPart("file", video.toFile(), "video/mp4").multiPart("mode", mode)
                .post("/api/searches").then().statusCode(201).extract().path<String>("id")
            return UUID.fromString(id)
        } finally {
            video.parent.toFile().deleteRecursively()
        }
    }

    private fun detail(id: UUID): JsonPath = api().get("/api/searches/$id").then().statusCode(200).extract().jsonPath()

    @Test
    fun `modo automatico varre o video, junta o produto repetido e gera o link afiliado`() {
        assumeTrue(ffmpegAvailable(), "ffmpeg ausente")
        search.results["serum principia"] = SearchHit("Sérum Vitamina C Principia 30ml", "https://www.amazon.com.br/Serum/dp/B0SERUM001", "https://img/s.jpg", "R$ 89,90")
        analyzer.respond = { input ->
            listOf(DetectedProduct("Sérum Vitamina C", "Principia", "serum principia vitamina c", "PRIMARY_FOCUS", "EXACT_BRANDED_PRODUCT", "beleza", 0, listOf(100, 100, 600, 600), 0.9))
                .takeIf { input.frames.isNotEmpty() } ?: emptyList()
        }
        pipeline.batchSize = 2
        val id = upload("AUTO")
        pipeline.process(id)

        val d = detail(id)
        assertEquals("DONE", d.getString("status"))
        assertTrue(d.getInt("framesSampled") >= 10)
        assertTrue(analyzer.calls.size >= 2, "vários lotes: ${analyzer.calls.size}")
        assertTrue(analyzer.calls.all { it.transcript == analyzer.transcript }, "transcrição única vai para todos os lotes")
        assertTrue(analyzer.calls.all { c -> c.frames.all { it.label.startsWith("tempo ") } })

        val items = d.getList<Map<String, Any>>("items")
        assertEquals(1, items.size, "o mesmo sérum de vários lotes vira um item")
        val item = items.single()
        assertEquals("FOUND", item["status"])
        assertEquals("Amazon Brasil", item["platformName"])
        assertEquals("https://www.amazon.com.br/dp/B0SERUM001?tag=conta-20", item["affiliateUrl"])
        assertTrue((item["timestamps"] as List<*>).size >= 2)
        assertTrue((item["cropUrl"] as String).startsWith("/m/"))

        // link curto redireciona para o link afiliado e conta o clique
        val code = URI(item["shortUrl"] as String).path
        given().redirects().follow(false).get(code).then().statusCode(302).header("Location", "https://www.amazon.com.br/dp/B0SERUM001?tag=conta-20")
        // a mídia assinada abre sem senha e some se a assinatura for adulterada
        given().get(item["cropUrl"] as String).then().statusCode(200).header("Content-Type", "image/jpeg")
        given().get((item["cropUrl"] as String).dropLast(3) + "abc").then().statusCode(404)
    }

    @Test
    fun `modo manual espera a escolha, analisa so os quadros escolhidos e usa a area marcada na busca visual`() {
        assumeTrue(ffmpegAvailable(), "ffmpeg ausente")
        analyzer.respond = { emptyList() } // a IA não reconhece nada: a área marcada ainda vira busca por imagem
        search.visualResults = listOf(
            SearchHit("Loja fora da lista", "https://www.outraloja.com/p/1"),
            SearchHit("Bolsa de palha", "https://www.amazon.com.br/Bolsa/dp/B0BOLSA001", null, "R$ 120,00"),
        )
        val id = upload("MANUAL")
        pipeline.process(id)
        val ready = detail(id)
        assertEquals("READY", ready.getString("status"))
        assertEquals(0, ready.getList<Any>("items").size)
        assertTrue(analyzer.calls.isEmpty(), "modo manual não chama a IA antes da escolha")

        // o player consegue pedir pedaços do vídeo
        val videoUrl = ready.getString("videoUrl")
        given().header("Range", "bytes=0-99").get(videoUrl).then().statusCode(206).header("Content-Range", org.hamcrest.Matchers.startsWith("bytes 0-99/"))

        val frames = listOf(mapOf("time" to 1.0, "box" to listOf(200, 200, 800, 800)), mapOf("time" to 4.5))
        api().contentType(ContentType.JSON).body(mapOf("frames" to frames)).post("/api/searches/$id/frames").then().statusCode(202)
        assertEquals("PROCESSING", detail(id).getString("status"))
        // o worker do JobRunr fica desligado nos testes: roda o job direto
        pipeline.analyzeManual(id, """[{"time":1.0,"box":[200,200,800,800]},{"time":4.5}]""")

        assertEquals(1, analyzer.calls.size)
        assertTrue(analyzer.calls.single().userSelected)
        assertEquals(2, analyzer.calls.single().frames.size)
        val d = detail(id)
        assertEquals("DONE", d.getString("status"))
        val item = d.getList<Map<String, Any>>("items").single()
        assertEquals("MANUAL", item["origin"])
        assertEquals("FOUND", item["status"])
        assertEquals("https://www.amazon.com.br/dp/B0BOLSA001?tag=conta-20", item["affiliateUrl"])
        assertTrue(search.visualCalls.single().jpeg.isNotEmpty())
        assertEquals(listOf(1.0), (item["timestamps"] as List<*>).map { (it as Number).toDouble() })
    }

    @Test
    fun `trocar a oferta gera o link da alternativa`() {
        assumeTrue(ffmpegAvailable(), "ffmpeg ausente")
        search.results["tenis"] = SearchHit("Tênis A", "https://www.amazon.com.br/A/dp/B0TENIS00A")
        analyzer.respond = { input ->
            if (input.frames.isEmpty()) emptyList()
            else listOf(DetectedProduct("Tênis branco", "Nike", "tenis nike branco", "PRIMARY_FOCUS", "EXACT_BRANDED_PRODUCT", null, 0, emptyList(), 0.9))
        }
        // segunda oferta na mesma loja para virar alternativa
        val extra = SearchHit("Tênis B", "https://www.amazon.com.br/B/dp/B0TENIS00B")
        search.results["nike"] = extra
        val id = upload("AUTO")
        pipeline.process(id)
        val item = detail(id).getList<Map<String, Any>>("items").single()
        val alternatives = item["alternatives"] as List<*>
        assertEquals(1, alternatives.size)
        val updated = api().contentType(ContentType.JSON).body(mapOf("index" to 0))
            .post("/api/searches/$id/items/${item["id"]}/offer").then().statusCode(200).extract().jsonPath()
        assertEquals("https://www.amazon.com.br/dp/B0TENIS00B?tag=conta-20", updated.getString("affiliateUrl"))
        assertEquals("Tênis A", (updated.getList<Map<String, Any>>("alternatives")).single()["title"])
    }

    @Test
    fun `painel exige a senha e recusa links internos`() {
        given().get("/api/searches").then().statusCode(401)
        given().header("Authorization", "Bearer errada").get("/api/config").then().statusCode(401)
        api().get("/api/config").then().statusCode(200).body("searchProvider", equalTo("serpapi"))
        api().contentType(ContentType.JSON).body(mapOf("url" to "http://localhost:8080/video.mp4", "mode" to "AUTO"))
            .post("/api/searches/url").then().statusCode(400).body("error", equalTo("Host não permitido."))
        api().contentType(ContentType.JSON).body(mapOf("frames" to listOf(mapOf("time" to 1.0, "box" to listOf(900, 0, 100, 100)))))
            .post("/api/searches/${UUID.randomUUID()}/frames").then().statusCode(400)
    }
}
