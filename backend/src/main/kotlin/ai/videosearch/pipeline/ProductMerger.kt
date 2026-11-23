package ai.videosearch.pipeline

import ai.videosearch.ai.DetectedProduct
import java.text.Normalizer

/** Uma detecção da IA já ligada ao quadro global e ao tempo do vídeo. */
data class Sighting(val product: DetectedProduct, val frame: Int, val time: Double)

/** Produto final, depois de juntar as aparições repetidas entre quadros e lotes. */
data class MergedProduct(val best: Sighting, val times: List<Double>) {
    val product get() = best.product
}

/**
 * Cada lote de quadros é analisado separadamente, então o mesmo produto costuma voltar várias vezes.
 * Junta por marca + nome (ou pela consulta de busca), fica com a aparição de maior confiança e guarda todos os tempos.
 * Se alguma aparição foi marcada como foco principal, o produto fica como foco principal.
 */
object ProductMerger {
    fun merge(sightings: List<Sighting>, similarity: Double = 0.6): List<MergedProduct> {
        val groups = mutableListOf<MutableList<Sighting>>()
        for (s in sightings.sortedByDescending { it.product.confidence }) {
            val g = groups.firstOrNull { same(it.first().product, s.product, similarity) }
            if (g == null) groups += mutableListOf(s) else g += s
        }
        return groups.map { g ->
            val best = g.first()
            val primary = g.any { it.product.layer == "PRIMARY_FOCUS" }
            val merged = best.copy(product = best.product.copy(layer = if (primary) "PRIMARY_FOCUS" else best.product.layer))
            MergedProduct(merged, g.map { it.time }.distinct().sorted())
        }
    }

    /** Ordena como o StoryShop: foco principal primeiro, depois por confiança. */
    fun rank(products: List<MergedProduct>, max: Int): List<MergedProduct> =
        products.sortedWith(compareBy<MergedProduct> { if (it.product.layer == "PRIMARY_FOCUS") 0 else 1 }
            .thenByDescending { it.times.size.coerceAtMost(3) }
            .thenByDescending { it.product.confidence })
            .take(max)

    fun same(a: DetectedProduct, b: DetectedProduct, similarity: Double): Boolean {
        val ba = a.brand?.let(::normalize)?.ifBlank { null }
        val bb = b.brand?.let(::normalize)?.ifBlank { null }
        if (ba != null && bb != null && ba != bb) return false
        if (normalize(a.searchQuery).isNotBlank() && normalize(a.searchQuery) == normalize(b.searchQuery)) return true
        return jaccard(tokens(a.label), tokens(b.label)) >= similarity
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]+"), " ").trim().replace(Regex("\\s+"), " ")

    private val stop = setOf("de", "da", "do", "das", "dos", "com", "para", "e", "o", "a", "em", "the", "of")
    fun tokens(s: String): Set<String> = normalize(s).split(' ').filter { it.length > 1 && it !in stop }.toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }
}
