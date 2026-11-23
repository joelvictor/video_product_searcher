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
}
