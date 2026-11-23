package ai.videosearch.search

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchProvidersTest {
    private val mapper = ObjectMapper()

    @Test
    fun `dominio exige fronteira de host`() {
        assertTrue(hostMatchesDomain("www.sephora.com.br", "sephora.com.br"))
        assertTrue(hostMatchesDomain("sephora.com.br", "www.sephora.com.br"))
        assertFalse(hostMatchesDomain("fake-sephora.com.br", "sephora.com.br"))
        assertFalse(hostMatchesDomain(null, "sephora.com.br"))
    }

    @Test
    fun `Brave filtra pelo dominio da loja e aplica o limite`() {
        val json = mapper.readTree(
            """{"web":{"results":[
              {"title":"Outra loja","url":"https://www.outra.com/p/1"},
              {"title":"Tênis A","url":"https://www.shopee.com.br/a","thumbnail":{"src":"https://img/a.jpg"},"description":"Por R$ 199,90 à vista"},
              {"title":"Tênis B","url":"https://shopee.com.br/b"},
              {"title":"Tênis C","url":"https://shopee.com.br/c"}]}}""",
        )
        val hits = BraveProductSearch.parseResults(json, "shopee.com.br", 2)
        assertEquals(listOf("Tênis A", "Tênis B"), hits.map { it.title })
        assertEquals("https://img/a.jpg", hits[0].imageUrl)
        assertEquals("R$ 199,90", hits[0].price)
    }

    @Test
    fun `DeepSeek junta fontes da ferramenta e citacoes sem repetir URL`() {
        val root = mapper.readTree(
            """{"choices":[{"message":{"content":"Encontrei na Amazon.",
                "server_tool_calls":[
                  {"id":"c1","type":"server_tool","kind":"search","status":"completed","sources":[
                    {"url":"https://www.amazon.com.br/Serum/dp/B0SERUM001","title":"Sérum Vitamina C 30ml","snippet":"R$ 89,90 com frete grátis","site_name":"Amazon"},
                    {"url":"https://blog.exemplo.com/resenha","title":"Resenha do sérum"}]},
                  {"id":"c2","type":"server_tool","kind":"image_search","status":"completed","sources":[
                    {"url":"https://www.magazineluiza.com.br/serum/p/abc","title":"","site_name":"Magalu","image_url":"https://img.magalu/serum.jpg"}]}],
                "annotations":[
                  {"type":"url_citation","url_citation":{"url":"https://www.amazon.com.br/Serum/dp/B0SERUM001","title":"dup"}},
                  {"type":"url_citation","url_citation":{"url":"https://www.amazon.com.br/Outro/dp/B0OUTRO001","title":"Outro"}}]}}]}""",
        )
        val hits = DeepSeekProductSearch.parseSources(root)
        assertEquals(
            listOf(
                "https://www.amazon.com.br/Serum/dp/B0SERUM001",
                "https://blog.exemplo.com/resenha",
                "https://www.magazineluiza.com.br/serum/p/abc",
                "https://www.amazon.com.br/Outro/dp/B0OUTRO001",
            ),
            hits.map { it.url },
        )
        assertEquals("R$ 89,90", hits[0].price)
        assertEquals("Magalu", hits[2].title)
        assertEquals("https://img.magalu/serum.jpg", hits[2].imageUrl)
        // a busca por domínio filtra depois pelo host da loja
        assertEquals(2, hits.count { hostMatchesDomain(hostOf(it.url), "amazon.com.br") })
    }

    @Test
    fun `DeepSeek sem fontes devolve vazio`() {
        assertTrue(DeepSeekProductSearch.parseSources(mapper.readTree("""{"choices":[{"message":{"content":"nada"}}]}""")).isEmpty())
    }

    @Test
    fun `preco em reais e extraido do texto`() {
        assertEquals("R$ 1.299,00", findPrice("de R$1.299,00 por"))
        assertNull(findPrice("sem preço"))
    }
}
