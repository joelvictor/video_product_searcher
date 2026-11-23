package ai.videosearch.pipeline

import ai.videosearch.ai.AnalysisInput
import ai.videosearch.ai.DetectedProduct
import ai.videosearch.ai.FrameInput
import ai.videosearch.ai.ProductAnalyzer
import ai.videosearch.media.DownloadException
import ai.videosearch.media.FrameTools
import ai.videosearch.media.SignedMediaUrls
import ai.videosearch.media.Transcriber
import ai.videosearch.media.VideoDownloader
import ai.videosearch.media.VideoScanner
import ai.videosearch.search.OfferResolver
import ai.videosearch.search.Resolution
import ai.videosearch.search.VisualQuery
import ai.videosearch.storage.MediaStorage
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import org.jobrunr.jobs.annotations.Job
import org.jobrunr.jobs.lambdas.IocJobLambda
import org.jobrunr.scheduling.JobScheduler
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** Quadro escolhido no modo manual: tempo no vídeo e, opcionalmente, a área do produto ([ymin, xmin, ymax, xmax] 0..1000). */
data class ManualFrame(val time: Double = 0.0, val box: List<Int>? = null)

/** Quadro pronto para a IA, com o que é preciso para gravar o item depois. */
private class PreparedFrame(val time: Double, val jpeg: ByteArray, val key: String, val userBox: Boolean)

/**
 * Fluxo de uma busca (mesmo desenho do pipeline do StoryShop: baixar, pré-processar, IA, busca, link afiliado):
 * 1. [prepare] baixa (URL) ou recebe (upload) o vídeo e grava no storage;
 * 2. modo automático: [scan] varre o vídeo inteiro em CPU, transcreve, analisa em lotes, junta, busca e gera links;
 * 3. modo manual: o painel mostra o vídeo e [analyzeManual] analisa só os quadros (e áreas) escolhidos pelo usuário.
 */
@ApplicationScoped
class SearchPipeline {
    private val log = Logger.getLogger(SearchPipeline::class.java)

    @Inject lateinit var jobScheduler: JobScheduler
    @Inject lateinit var storage: MediaStorage
    @Inject lateinit var downloader: VideoDownloader
    @Inject lateinit var scanner: VideoScanner
    @Inject lateinit var transcriber: Transcriber
    @Inject lateinit var analyzer: ProductAnalyzer
    @Inject lateinit var resolver: OfferResolver
    @Inject lateinit var mediaUrls: SignedMediaUrls
    @Inject lateinit var mapper: com.fasterxml.jackson.databind.ObjectMapper

    @ConfigProperty(name = "vps.download.max-duration", defaultValue = "900")
    var maxDuration: Int = 900

    @ConfigProperty(name = "vps.scan.batch-size", defaultValue = "8")
    var batchSize: Int = 8

    @ConfigProperty(name = "vps.scan.parallel-batches", defaultValue = "3")
    var parallelBatches: Int = 3

    @ConfigProperty(name = "vps.scan.max-products", defaultValue = "25")
    var maxProducts: Int = 25

    // ---------------------------------------------------------------- entrada

    fun createFromUpload(file: Path, fileName: String?, mode: SearchMode): UUID {
        val id = QuarkusTransaction.requiringNew().call {
            Search().also { it.sourceType = SourceType.UPLOAD; it.mode = mode; it.title = fileName; it.persist() }.id
        }
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in VIDEO_EXT } ?: "mp4"
        storage.putFile(videoKey(id, ext), file)
        QuarkusTransaction.requiringNew().run { Search.findById(id)!!.videoKey = videoKey(id, ext) }
        enqueue(id)
        return id
    }

    fun createFromUrl(url: String, mode: SearchMode): UUID {
        ai.videosearch.media.VideoDownloader.validate(url)
        val id = QuarkusTransaction.requiringNew().call {
            Search().also { it.sourceType = SourceType.URL; it.sourceUrl = url.trim(); it.mode = mode; it.persist() }.id
        }
        enqueue(id)
        return id
    }

    private fun enqueue(id: UUID) {
        jobScheduler.enqueue(IocJobLambda<SearchPipeline> { it.process(id) })
    }

    fun enqueueManual(id: UUID, frames: List<ManualFrame>) {
        require(frames.isNotEmpty()) { "Escolha ao menos um quadro." }
        require(frames.size <= MAX_MANUAL_FRAMES) { "Escolha no máximo $MAX_MANUAL_FRAMES quadros por vez." }
        QuarkusTransaction.requiringNew().run {
            val s = Search.findById(id) ?: throw NoSuchElementException("busca não encontrada")
            check(s.videoKey != null && !s.mediaDeleted) { "O vídeo desta busca não está mais disponível." }
            check(s.status in setOf(SearchStatus.READY, SearchStatus.DONE, SearchStatus.FAILED)) { "A busca ainda está em andamento." }
            s.touch(SearchStatus.PROCESSING, "Na fila")
            s.errorMessage = null
        }
        val payload = mapper.writeValueAsString(frames)
        jobScheduler.enqueue(IocJobLambda<SearchPipeline> { it.analyzeManual(id, payload) })
    }

    // ---------------------------------------------------------------- jobs

    @Job(name = "Processar busca %0", retries = 0)
    fun process(searchId: UUID) {
        val s = QuarkusTransaction.requiringNew().call { Search.findById(searchId) } ?: return
        if (s.status != SearchStatus.RECEIVED) return
        guarded(searchId) {
            val key = s.videoKey ?: download(searchId, s.sourceUrl!!)
            val video = storage.path(key)
            if (!scanner.hasVideoStream(video)) throw DownloadException("O arquivo enviado não é um vídeo.")
            val duration = scanner.duration(video)
            if (duration > maxDuration) throw DownloadException("O vídeo tem ${FrameTools.clock(duration)}; o limite é ${FrameTools.clock(maxDuration.toDouble())}.")
            update(searchId) { it.durationSeconds = duration }
            if (s.mode == SearchMode.MANUAL) update(searchId) { it.touch(SearchStatus.READY, null) }
            else scan(searchId, video)
        }
    }

    @Job(name = "Analisar quadros escolhidos da busca %0", retries = 0)
    fun analyzeManual(searchId: UUID, payload: String) {
        val frames: List<ManualFrame> = mapper.readValue(payload, object : com.fasterxml.jackson.core.type.TypeReference<List<ManualFrame>>() {})
        guarded(searchId) {
            val s = QuarkusTransaction.requiringNew().call { Search.findById(searchId)!! }
            val video = storage.path(s.videoKey!!)
            val end = (s.durationSeconds ?: Double.MAX_VALUE) - 0.05
            progress(searchId, "Extraindo ${frames.size} quadro(s)")
            val jpegs = scanner.framesAt(video, frames.map { it.time.coerceIn(0.0, end.coerceAtLeast(0.0)) })
            val prepared = frames.mapIndexed { i, f ->
                val full = jpegs[i]
                val ms = (f.time * 1000).toLong()
                val box = f.box?.takeIf { it.size == 4 }
                // com área marcada, a IA recebe só o recorte: o produto escolhido vira o assunto do quadro
                val jpeg = if (box != null) FrameTools.crop(full, box, margin = 0.02) else full
                val key = "${base(searchId)}/manual/t${ms}_${UUID.randomUUID().toString().take(6)}.jpg"
                storage.put(key, jpeg)
                PreparedFrame(f.time, jpeg, key, box != null)
            }
            val transcript = s.transcript
            analyzeAndPersist(searchId, prepared, transcript, s.caption, SearchMode.MANUAL)
        }
    }

    // ---------------------------------------------------------------- etapas

    private fun download(searchId: UUID, url: String): String {
        progress(searchId, "Baixando o vídeo", SearchStatus.DOWNLOADING)
        val dl = downloader.download(url)
        val ext = dl.file.fileName.toString().substringAfterLast('.', "mp4").lowercase().takeIf { it in VIDEO_EXT } ?: "mp4"
        val key = videoKey(searchId, ext)
        storage.putFile(key, dl.file)
        runCatching { Files.deleteIfExists(dl.file); dl.file.parent?.toFile()?.takeIf { it.name.startsWith("vps-dl") }?.deleteRecursively() }
        update(searchId) {
            it.videoKey = key
            it.title = dl.title?.take(300) ?: it.title
            it.caption = dl.description?.take(4000)
        }
        return key
    }

    private fun scan(searchId: UUID, video: Path) {
        progress(searchId, "Varrendo o vídeo inteiro", SearchStatus.PROCESSING)
        val scan = scanner.scan(video)
        update(searchId) { it.framesSampled = scan.sampled }
        progress(searchId, "Transcrevendo a fala")
        val transcript = transcriber.transcribe(video)
        update(searchId) { it.transcript = transcript }
        val caption = QuarkusTransaction.requiringNew().call { Search.findById(searchId)!!.caption }
        val prepared = scan.frames.map { f ->
            val key = "${base(searchId)}/frames/t${(f.time * 1000).toLong()}.jpg"
            storage.put(key, f.jpeg)
            PreparedFrame(f.time, f.jpeg, key, false)
        }
        analyzeAndPersist(searchId, prepared, transcript, caption, SearchMode.AUTO)
    }

    private fun analyzeAndPersist(searchId: UUID, frames: List<PreparedFrame>, transcript: String?, caption: String?, origin: SearchMode) {
        val batches = frames.chunked(batchSize.coerceAtLeast(1))
        val done = java.util.concurrent.atomic.AtomicInteger()
        val gate = Semaphore(parallelBatches.coerceAtLeast(1))
        progress(searchId, "Analisando ${frames.size} quadros (0/${batches.size} lotes)")
        val sightings = Executors.newVirtualThreadPerTaskExecutor().use { pool ->
            batches.mapIndexed { b, batch ->
                pool.submit<List<Sighting>> {
                    gate.acquire()
                    try {
                        val input = AnalysisInput(
                            frames = batch.map { FrameInput(it.jpeg, "tempo ${FrameTools.clock(it.time)}" + if (it.userBox) ", área escolhida pelo usuário" else "") },
                            transcript = transcript,
                            caption = caption,
                            userSelected = origin == SearchMode.MANUAL,
                        )
                        val items = analyzeWithRetry(input).items
                        progress(searchId, "Analisando ${frames.size} quadros (${done.incrementAndGet()}/${batches.size} lotes)")
                        items.map { p ->
                            val local = p.frameIndex.coerceIn(0, batch.size - 1)
                            Sighting(p.copy(frameIndex = b * batchSize + local), b * batchSize + local, batch[local].time)
                        }
                    } finally {
                        gate.release()
                    }
                }
            }.flatMap { it.get() }
        }
        // no modo manual, quadro com área marcada em que a IA não achou nada ainda vira busca visual do recorte
        val manualFallback = if (origin == SearchMode.MANUAL) frames.withIndex()
            .filter { (i, f) -> f.userBox && sightings.none { it.frame == i } }
            .map { (i, f) -> Sighting(DetectedProduct("Produto selecionado", null, "", "PRIMARY_FOCUS", "VISUAL_FASHION_ITEM", null, i, emptyList(), 0.7), i, f.time) }
        else emptyList()

        val products = ProductMerger.rank(ProductMerger.merge(sightings + manualFallback), maxProducts)
        progress(searchId, "Buscando ${products.size} produto(s) nas lojas")

        val crops = products.map { m ->
            val frame = frames[m.best.frame]
            val crop = FrameTools.crop(frame.jpeg, m.product.box)
            val key = "${base(searchId)}/crops/${UUID.randomUUID()}.jpg"
            storage.put(key, crop)
            key to crop
        }
        val resolutions = Executors.newVirtualThreadPerTaskExecutor().use { pool ->
            val gateSearch = Semaphore(6)
            products.mapIndexed { i, m ->
                pool.submit<Resolution?> {
                    gateSearch.acquire()
                    try { resolveProduct(m.product, crops[i].first, crops[i].second) } finally { gateSearch.release() }
                }
            }.map { it.get() }
        }

        QuarkusTransaction.requiringNew().run {
            var number = SearchItem.nextNumber(searchId)
            products.forEachIndexed { i, m ->
                persistItem(searchId, number++, origin, m, frames[m.best.frame].key, crops[i].first, resolutions[i])
            }
            val s = Search.findById(searchId)!!
            s.framesAnalyzed = (if (origin == SearchMode.AUTO) 0 else s.framesAnalyzed ?: 0) + frames.size
            s.touch(SearchStatus.DONE, if (products.isEmpty()) "Nenhum produto identificado nesses quadros" else null)
        }
    }

    private fun analyzeWithRetry(input: AnalysisInput) =
        runCatching { analyzer.analyze(input) }.getOrElse {
            log.warnf("primeira tentativa da IA falhou (%s), repetindo", it.message)
            Thread.sleep(1500)
            analyzer.analyze(input)
        }

    /** Rota A (texto) para itens com marca e Rota B (imagem) para moda e decoração sem marca, como no StoryShop. */
    private fun resolveProduct(p: DetectedProduct, cropKey: String, crop: ByteArray): Resolution? {
        if (p.confidence < MIN_CONFIDENCE) return Resolution(null, emptyList(), null, "Confiança baixa: confira o produto antes de buscar.")
        val query = p.searchQuery.ifBlank { p.label }
        return runCatching {
            if (p.detectionType == "VISUAL_FASHION_ITEM") resolver.resolveVisual(VisualQuery(mediaUrls.absolute(cropKey), crop), query)
            else resolver.resolve(query)
        }
            .onFailure { log.warnf("resolução de '%s' falhou: %s", p.label, it.message) }
            .getOrNull()
    }

    private fun persistItem(searchId: UUID, number: Int, origin: SearchMode, m: MergedProduct, frameKey: String, cropKey: String, r: Resolution?) {
        val p = m.product
        SearchItem().also {
            it.searchId = searchId
            it.itemNumber = number
            it.origin = origin
            it.label = p.label.take(300)
            it.brand = p.brand
            it.searchQuery = p.searchQuery.ifBlank { p.label }
            it.relevanceLayer = p.layer
            it.detectionType = p.detectionType
            it.category = p.category?.take(80)
            it.confidence = BigDecimal.valueOf(p.confidence.coerceIn(0.0, 1.0)).setScale(3, RoundingMode.HALF_UP)
            it.timestamps = m.times
            it.boundingBox = p.box.takeIf { b -> b.size == 4 } ?: emptyList()
            it.frameKey = frameKey
            it.cropKey = cropKey
            val link = r?.link
            if (r?.primary != null && link != null) {
                it.status = ItemStatus.FOUND
                it.platformId = link.platform.id
                it.productTitle = r.primary.title
                it.productImageUrl = r.primary.imageUrl
                it.priceDisplay = r.primary.price?.take(50)
                it.originalUrl = r.primary.url
                it.shortLinkId = link.shortLink.id
                it.affiliateUrl = link.affiliateUrl
            } else {
                it.status = ItemStatus.NOT_FOUND
            }
            it.alternativeOffers = r?.alternatives ?: emptyList()
            it.note = r?.note ?: if (r == null) "A busca nas lojas falhou." else null
            it.persist()
        }
    }

    // ---------------------------------------------------------------- utilidades

    private fun guarded(searchId: UUID, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.errorf(e, "falha na busca %s", searchId)
            // erros dos lotes chegam embrulhados em ExecutionException: mostra a causa original
            val root = generateSequence(e as Throwable) { it.cause }.last()
            val message = if (root is DownloadException) root.message else "Falha ao processar: ${root.message?.take(300)}"
            update(searchId) { it.touch(SearchStatus.FAILED, null); it.errorMessage = message }
        }
    }

    private fun progress(searchId: UUID, text: String, status: SearchStatus? = null) = update(searchId) { it.touch(status, text) }

    private fun update(searchId: UUID, change: (Search) -> Unit) {
        QuarkusTransaction.requiringNew().run { Search.findById(searchId)?.let(change) }
    }

    companion object {
        const val MIN_CONFIDENCE = 0.5
        const val MAX_MANUAL_FRAMES = 12
        val VIDEO_EXT = setOf("mp4", "mov", "webm", "mkv", "m4v")
        fun base(id: UUID) = "searches/$id"
        fun videoKey(id: UUID, ext: String) = "${base(id)}/video.$ext"
    }
}
