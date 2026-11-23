package ai.videosearch.affiliate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AffiliateLinkEngineTest {

    @Test
    fun `amazon extrai ASIN e aplica a tag da conta`() {
        val url = AffiliateLinkEngine.build(
            "https://www.amazon.com.br/dp/{asin}?tag={influencer.amazon_tag}",
            "https://www.amazon.com.br/Protetor-Solar-Roche/dp/B08XYZ1234/ref=sr_1_1?keywords=x",
            mapOf("amazon_tag" to "fulana-20"),
        )
        assertEquals("https://www.amazon.com.br/dp/B08XYZ1234?tag=fulana-20", url)
    }

    @Test
    fun `awin usa parametro da plataforma e URL codificada`() {
        val url = AffiliateLinkEngine.build(
            "https://www.awin1.com/cread.php?awinmid={merchant_id}&awinaffid={influencer.awin_id}&ued={encoded_original_url}",
            "https://www.sephora.com.br/produto?id=1&cor=2",
            mapOf("awin_id" to "999"),
            mapOf("merchant_id" to "17743"),
        )
        assertEquals(
            "https://www.awin1.com/cread.php?awinmid=17743&awinaffid=999&ued=https%3A%2F%2Fwww.sephora.com.br%2Fproduto%3Fid%3D1%26cor%3D2",
            url,
        )
    }

    @Test
    fun `falta de credencial gera erro explicito`() {
        val e = assertThrows<MissingMacroException> {
            AffiliateLinkEngine.build("https://x.com/?s={influencer.source_id}", "https://loja.com/p", emptyMap())
        }
        assertEquals("influencer.source_id", e.macro)
    }

    @Test
    fun `shopee e mercado livre usam a URL sem query`() {
        val shopee = AffiliateLinkEngine.build(
            "https://shope.ee/an_redir?origin_link={encoded_clean_url}&affiliate_id={influencer.affiliate_id}",
            "https://shopee.com.br/Bolsa-de-Palha-i.123.456?sp_atk=abc&xptdk=1#frag",
            mapOf("affiliate_id" to "987"),
        )
        assertEquals("https://shope.ee/an_redir?origin_link=https%3A%2F%2Fshopee.com.br%2FBolsa-de-Palha-i.123.456&affiliate_id=987", shopee)

        val ml = AffiliateLinkEngine.build(
            "{clean_url}?matt_tool={influencer.matt_tool}&matt_word={influencer.matt_word}",
            "https://www.mercadolivre.com.br/bolsa-palha/p/MLB123?pdp_filters=x#polycard_client=search",
            mapOf("matt_tool" to "111", "matt_word" to "fulana"),
        )
        assertEquals("https://www.mercadolivre.com.br/bolsa-palha/p/MLB123?matt_tool=111&matt_word=fulana", ml)
    }
}

