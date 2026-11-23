package ai.videosearch.search

/** O host precisa ser o domínio da loja ou um subdomínio dele (`fake-loja.com.br` não vale para `loja.com.br`). */
fun hostMatchesDomain(host: String?, domain: String): Boolean {
    val d = domain.removePrefix("www.").lowercase()
    val h = host.orEmpty().lowercase()
    return h == d || h.endsWith(".$d")
}

fun hostOf(url: String): String? = runCatching { java.net.URI(url).host }.getOrNull()

private val priceRegex = Regex("""R\$\s?\d{1,3}(?:\.\d{3})*(?:,\d{2})?""")

/** Primeiro preço em reais que aparecer num trecho de texto (ex.: snippet de busca). */
fun findPrice(text: String?): String? = text?.let { priceRegex.find(it)?.value?.let { v -> "R$ " + v.removePrefix("R$").trimStart() } }

data class SearchHit(
    val title: String,
    val url: String,
    val imageUrl: String? = null,
    val price: String? = null,
)

/** Imagem do produto para a busca visual: URL pública assinada (SerpAPI/Lens) e os bytes (DeepSeek). */
class VisualQuery(val imageUrl: String, val jpeg: ByteArray)

/** Busca de produtos nas lojas parceiras (Rota A: texto por domínio; Rota B: imagem). */
interface ProductSearch {
    /** Equivale a `site:{domain} {query}`, restrito à loja. */
    fun searchInDomain(domain: String, query: String, limit: Int = 3): List<SearchHit>

    /** Busca reversa por imagem. Pode devolver resultados de qualquer site; quem chama filtra pelas lojas. */
    fun visualSearch(image: VisualQuery, limit: Int = 10): List<SearchHit>
}
