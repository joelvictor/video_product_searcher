package ai.videosearch.support

import ai.videosearch.ai.AnalysisInput
import ai.videosearch.ai.AnalysisResult
import ai.videosearch.ai.DetectedProduct
import ai.videosearch.ai.ProductAnalyzer
import ai.videosearch.search.ProductSearch
import ai.videosearch.search.SearchHit
import ai.videosearch.search.VisualQuery
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import java.util.concurrent.CopyOnWriteArrayList

@Alternative @Priority(1) @ApplicationScoped
class FakeAnalyzer : ProductAnalyzer {
    val calls = CopyOnWriteArrayList<AnalysisInput>()
    /** Produtos devolvidos para cada lote (recebe o lote). */
    var respond: (AnalysisInput) -> List<DetectedProduct> = { emptyList() }
    var transcript = "[00:01] olha esse sérum da Principia"

    override fun analyze(input: AnalysisInput): AnalysisResult { calls += input; return AnalysisResult(respond(input)) }
    override fun transcribe(audio: ByteArray, mimeType: String) = transcript
}

@Alternative @Priority(1) @ApplicationScoped
class FakeSearch : ProductSearch {
    /** consulta (parte do texto) -> resultado. */
    val results = mutableMapOf<String, SearchHit>()
    var visualResults: List<SearchHit> = emptyList()
    val visualCalls = CopyOnWriteArrayList<VisualQuery>()

    override fun searchInDomain(domain: String, query: String, limit: Int): List<SearchHit> =
        results.entries.filter { query.contains(it.key, ignoreCase = true) && it.value.url.contains(domain) }.map { it.value }.take(limit)

    override fun visualSearch(image: VisualQuery, limit: Int): List<SearchHit> { visualCalls += image; return visualResults }
}
