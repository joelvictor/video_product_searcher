package ai.videosearch.pipeline

import ai.videosearch.ai.DetectedProduct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProductMergerTest {
    private fun p(label: String, brand: String?, query: String, conf: Double, layer: String = "SCENE_ITEM") =
        DetectedProduct(label, brand, query, layer, "EXACT_BRANDED_PRODUCT", null, 0, emptyList(), conf)

    @Test
    fun `mesmo produto em lotes diferentes vira um so com todos os tempos`() {
        val merged = ProductMerger.merge(
            listOf(
                Sighting(p("Sérum Vitamina C 30ml", "Principia", "serum vitamina c principia", 0.7), 0, 3.0),
                Sighting(p("Serum vitamina C", "PRINCIPIA", "sérum principia vitamina c", 0.9, "PRIMARY_FOCUS"), 9, 42.0),
                Sighting(p("Sérum Vitamina C", "Principia", "serum vitamina c principia", 0.8), 4, 12.5),
                Sighting(p("Tênis branco", null, "tenis branco", 0.8), 2, 5.0),
            ),
        )
        assertEquals(2, merged.size)
        val serum = merged.first { it.product.brand.equals("principia", true) }
        assertEquals(listOf(3.0, 12.5, 42.0), serum.times)
        assertEquals(0.9, serum.product.confidence)
        assertEquals("PRIMARY_FOCUS", serum.product.layer)
        assertEquals(9, serum.best.frame)
    }

    @Test
    fun `marcas diferentes nunca se juntam`() {
        val merged = ProductMerger.merge(
            listOf(
                Sighting(p("Protetor solar facial", "La Roche-Posay", "protetor la roche", 0.8), 0, 1.0),
                Sighting(p("Protetor solar facial", "Neutrogena", "protetor neutrogena", 0.8), 1, 2.0),
            ),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `ranking poe foco principal primeiro e respeita o maximo`() {
        val merged = ProductMerger.merge(
            listOf(
                Sighting(p("Cadeira", null, "cadeira", 0.95), 0, 1.0),
                Sighting(p("Batom vermelho", "MAC", "batom mac", 0.7, "PRIMARY_FOCUS"), 1, 2.0),
                Sighting(p("Luminária", null, "luminaria", 0.6), 2, 3.0),
            ),
        )
        assertEquals(listOf("Batom vermelho", "Cadeira"), ProductMerger.rank(merged, 2).map { it.product.label })
    }
}
