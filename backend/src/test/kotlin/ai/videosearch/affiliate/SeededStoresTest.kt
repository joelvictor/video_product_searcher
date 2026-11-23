package ai.videosearch.affiliate

import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class SeededStoresTest {
    private fun store(slug: String) = QuarkusTransaction.requiringNew().call {
        AffiliatePlatform.find("slug", slug).firstResult()
    }

    @Test
    fun `shopee e mercado livre vem cadastradas e reconhecem seus links`() {
        val shopee = store("shopee-br")!!
        assertEquals("shopee.com.br", shopee.searchDomain)
        assertTrue(shopee.matches("https://shopee.com.br/Bolsa-i.1.2"))
        assertTrue(shopee.matches("https://shope.ee/abc"))
        assertFalse(shopee.matches("https://www.amazon.com.br/dp/B0X"))

        val ml = store("mercado-livre")!!
        assertEquals("mercadolivre.com.br", ml.searchDomain)
        assertTrue(ml.matches("https://www.mercadolivre.com.br/bolsa/p/MLB1"))
        assertTrue(ml.matches("https://produto.mercadolivre.com.br/MLB-123-bolsa-_JM"))
        assertTrue(ml.matches("https://meli.la/2abc"))
        assertFalse(ml.matches("https://www.shopee.com.br/x"))
        assertNotNull(ml.affiliateUrlTemplate)
        assertEquals("https://affiliate.shopee.com.br/", shopee.helpUrl)
        assertTrue(QuarkusTransaction.requiringNew().call { AffiliatePlatform.listAll().all { it.helpUrl?.startsWith("https://") == true } })
    }
}
