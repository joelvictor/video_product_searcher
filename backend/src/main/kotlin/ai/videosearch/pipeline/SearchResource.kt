package ai.videosearch.pipeline

import ai.videosearch.affiliate.AffiliatePlatform
import ai.videosearch.links.ShortLink
import ai.videosearch.media.SignedMediaUrls
import ai.videosearch.search.Offer
import ai.videosearch.search.OfferResolver
import ai.videosearch.storage.MediaStorage
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.time.OffsetDateTime
import java.util.UUID

data class CreatedSearch(val id: UUID)

data class SearchSummary(
    val id: UUID,
    val sourceType: SourceType,
    val sourceUrl: String?,
    val title: String?,
    val mode: SearchMode,
    val status: SearchStatus,
    val progress: String?,
    val createdAt: OffsetDateTime,
    val itemCount: Long,
    val foundCount: Long,
    val thumbnailUrl: String?,
)

data class ItemView(
    val id: UUID,
    val itemNumber: Int,
    val origin: SearchMode,
    val label: String,
    val brand: String?,
    val searchQuery: String,
    val relevanceLayer: String,
    val detectionType: String,
    val category: String?,
    val confidence: Double,
    val timestamps: List<Double>,
    val frameUrl: String?,
    val cropUrl: String?,
    val status: ItemStatus,
    val platformName: String?,
    val productTitle: String?,
    val productImageUrl: String?,
    val priceDisplay: String?,
    val originalUrl: String?,
    val shortUrl: String?,
    val affiliateUrl: String?,
    val alternatives: List<Offer>,
    val note: String?,
)

data class SearchDetail(
    val id: UUID,
    val sourceType: SourceType,
    val sourceUrl: String?,
    val title: String?,
    val caption: String?,
    val mode: SearchMode,
    val status: SearchStatus,
    val progress: String?,
    val errorMessage: String?,
    val durationSeconds: Double?,
    val framesSampled: Int?,
    val framesAnalyzed: Int?,
    val transcript: String?,
    val videoUrl: String?,
    val mediaDeleted: Boolean,
    val createdAt: OffsetDateTime,
    val items: List<ItemView>,
)

data class UrlRequest(val url: String = "", val mode: SearchMode = SearchMode.AUTO)
data class ManualRequest(val frames: List<ManualFrame> = emptyList())
data class SwitchOfferRequest(val index: Int = 0)

@Path("/api/searches")
@Produces(MediaType.APPLICATION_JSON)
class SearchResource {
    @Inject lateinit var pipeline: SearchPipeline
    @Inject lateinit var urls: SignedMediaUrls
    @Inject lateinit var storage: MediaStorage
    @Inject lateinit var resolver: OfferResolver
    @Inject lateinit var links: ai.videosearch.links.AffiliateLinkService

    /** Upload do arquivo de vídeo (multipart: `file`, `mode`). */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun upload(@RestForm("file") file: FileUpload?, @RestForm("mode") mode: String?): Response {
        val f = file ?: throw BadRequestException("Envie o arquivo de vídeo no campo 'file'.")
        val id = pipeline.createFromUpload(f.uploadedFile(), f.fileName(), parseMode(mode))
        return Response.status(201).entity(CreatedSearch(id)).build()
    }

    /** Vídeo por link (TikTok, YouTube, Instagram, outros ou link direto). */
    @POST
    @Path("/url")
    @Consumes(MediaType.APPLICATION_JSON)
    fun fromUrl(req: UrlRequest): Response {
        if (req.url.isBlank()) throw BadRequestException("Informe o link do vídeo.")
        return Response.status(201).entity(CreatedSearch(pipeline.createFromUrl(req.url, req.mode))).build()
    }

    @GET
    @Transactional
    fun list(): List<SearchSummary> = Search.latest(50).map { s ->
        val items = SearchItem.ofSearch(s.id)
        SearchSummary(
            s.id, s.sourceType, s.sourceUrl, s.title, s.mode, s.status, s.progress, s.createdAt,
            items.size.toLong(), items.count { it.status == ItemStatus.FOUND }.toLong(),
            items.firstOrNull()?.cropKey?.takeIf(storage::exists)?.let(urls::path),
        )
    }

    @GET
    @Path("/{id}")
    @Transactional
    fun get(@PathParam("id") id: UUID): SearchDetail {
        val s = Search.findById(id) ?: throw NotFoundException()
        val platforms = AffiliatePlatform.listAll().associateBy { it.id }
        val items = SearchItem.ofSearch(id).map { i ->
            ItemView(
                i.id, i.itemNumber, i.origin, i.label, i.brand, i.searchQuery, i.relevanceLayer, i.detectionType, i.category,
                i.confidence.toDouble(), i.timestamps,
                i.frameKey?.takeIf(storage::exists)?.let(urls::path),
                i.cropKey?.takeIf(storage::exists)?.let(urls::path),
                i.status, i.platformId?.let { platforms[it]?.displayName }, i.productTitle, i.productImageUrl, i.priceDisplay,
                i.originalUrl, i.shortLinkId?.let { ShortLink.findById(it) }?.let { links.shortUrl(it.code) }, i.affiliateUrl,
                i.alternativeOffers, i.note,
            )
        }
        val video = s.videoKey?.takeIf { !s.mediaDeleted && storage.exists(it) }?.let(urls::path)
        return SearchDetail(
            s.id, s.sourceType, s.sourceUrl, s.title, s.caption, s.mode, s.status, s.progress, s.errorMessage, s.durationSeconds,
            s.framesSampled, s.framesAnalyzed, s.transcript, video, s.mediaDeleted, s.createdAt, items,
        )
    }

    /** Modo manual: analisa os quadros (e áreas) que o usuário escolheu no player. */
    @POST
    @Path("/{id}/frames")
    @Consumes(MediaType.APPLICATION_JSON)
    fun manual(@PathParam("id") id: UUID, req: ManualRequest): Response {
        req.frames.forEach { f ->
            if (f.time < 0) throw BadRequestException("Tempo inválido.")
            f.box?.let { b -> if (b.size != 4 || b.any { it !in 0..1000 } || b[2] <= b[0] || b[3] <= b[1]) throw BadRequestException("Área inválida.") }
        }
        pipeline.enqueueManual(id, req.frames)
        return Response.accepted().build()
    }

    /** Troca a oferta principal por uma alternativa e gera o link afiliado dela (como a troca de loja do StoryShop). */
    @POST
    @Path("/{id}/items/{itemId}/offer")
    @Consumes(MediaType.APPLICATION_JSON)
    fun switchOffer(@PathParam("id") id: UUID, @PathParam("itemId") itemId: UUID, req: SwitchOfferRequest): ItemView {
        val (item, offer) = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call {
            val i = SearchItem.findById(itemId)?.takeIf { it.searchId == id } ?: throw NotFoundException()
            i to (i.alternativeOffers.getOrNull(req.index) ?: throw BadRequestException("Oferta inexistente."))
        }
        val link = resolver.linkFor(offer) ?: throw BadRequestException("Não foi possível gerar o link dessa loja. Confira as credenciais em Lojas.")
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run {
            val i = SearchItem.findById(item.id)!!
            val previous = i.originalUrl?.let { url ->
                Offer(i.platformId?.toString() ?: "", i.platformId?.let { AffiliatePlatform.findById(it)?.displayName } ?: "", i.productTitle ?: "", url, i.productImageUrl, i.priceDisplay)
            }
            i.alternativeOffers = (i.alternativeOffers.filterIndexed { idx, _ -> idx != req.index } + listOfNotNull(previous))
            i.status = ItemStatus.FOUND
            i.platformId = link.platform.id
            i.productTitle = offer.title
            i.productImageUrl = offer.imageUrl
            i.priceDisplay = offer.price?.take(50)
            i.originalUrl = offer.url
            i.shortLinkId = link.shortLink.id
            i.affiliateUrl = link.affiliateUrl
            i.note = null
        }
        return get(id).items.first { it.id == itemId }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    fun delete(@PathParam("id") id: UUID): Response {
        val s = Search.findById(id) ?: throw NotFoundException()
        if (s.status in setOf(SearchStatus.DOWNLOADING, SearchStatus.PROCESSING)) throw IllegalStateException("Aguarde a busca terminar para apagar.")
        SearchItem.delete("searchId", id)
        s.delete()
        storage.deletePrefix(SearchPipeline.base(id))
        return Response.noContent().build()
    }

    private fun parseMode(mode: String?) =
        runCatching { SearchMode.valueOf(mode?.trim()?.uppercase() ?: "AUTO") }.getOrElse { throw BadRequestException("Modo inválido.") }
}
