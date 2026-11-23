package ai.videosearch.pipeline

import ai.videosearch.search.Offer
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class SourceType { UPLOAD, URL }
enum class SearchMode { AUTO, MANUAL }
enum class SearchStatus { RECEIVED, DOWNLOADING, READY, PROCESSING, DONE, FAILED }
enum class ItemStatus { FOUND, NOT_FOUND }

@Entity
@Table(name = "searches")
class Search : PanacheEntityBase {
    @Id
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    lateinit var sourceType: SourceType

    @Column(name = "source_url")
    var sourceUrl: String? = null
    var title: String? = null
    var caption: String? = null

    @Enumerated(EnumType.STRING)
    lateinit var mode: SearchMode

    @Enumerated(EnumType.STRING)
    var status: SearchStatus = SearchStatus.RECEIVED
    var progress: String? = null

    @Column(name = "error_message")
    var errorMessage: String? = null

    @Column(name = "video_key")
    var videoKey: String? = null

    @Column(name = "duration_seconds")
    var durationSeconds: Double? = null

    @Column(name = "frames_sampled")
    var framesSampled: Int? = null

    @Column(name = "frames_analyzed")
    var framesAnalyzed: Int? = null
    var transcript: String? = null

    @Column(name = "media_deleted")
    var mediaDeleted: Boolean = false

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun touch(status: SearchStatus? = null, progress: String? = null) {
        status?.let { this.status = it }
        this.progress = progress
        updatedAt = OffsetDateTime.now()
    }

    companion object : PanacheCompanionBase<Search, UUID> {
        fun latest(limit: Int): List<Search> = find("order by createdAt desc").page(0, limit).list()
        fun withExpiredMedia(before: OffsetDateTime): List<Search> =
            list("mediaDeleted = false and createdAt < ?1 and status in ?2", before, listOf(SearchStatus.DONE, SearchStatus.FAILED, SearchStatus.READY))
    }
}

@Entity
@Table(name = "search_items")
class SearchItem : PanacheEntityBase {
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "search_id")
    lateinit var searchId: UUID

    @Column(name = "item_number")
    var itemNumber: Int = 0

    @Enumerated(EnumType.STRING)
    lateinit var origin: SearchMode
    lateinit var label: String
    var brand: String? = null

    @Column(name = "search_query")
    lateinit var searchQuery: String

    @Column(name = "relevance_layer")
    lateinit var relevanceLayer: String

    @Column(name = "detection_type")
    lateinit var detectionType: String
    var category: String? = null
    var confidence: BigDecimal = BigDecimal.ZERO

    @JdbcTypeCode(SqlTypes.JSON)
    var timestamps: List<Double> = emptyList()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box")
    var boundingBox: List<Int> = emptyList()

    @Column(name = "frame_key")
    var frameKey: String? = null

    @Column(name = "crop_key")
    var cropKey: String? = null

    @Enumerated(EnumType.STRING)
    var status: ItemStatus = ItemStatus.NOT_FOUND

    @Column(name = "platform_id")
    var platformId: UUID? = null

    @Column(name = "product_title")
    var productTitle: String? = null

    @Column(name = "product_image_url")
    var productImageUrl: String? = null

    @Column(name = "price_display")
    var priceDisplay: String? = null

    @Column(name = "original_url")
    var originalUrl: String? = null

    @Column(name = "short_link_id")
    var shortLinkId: UUID? = null

    @Column(name = "affiliate_url")
    var affiliateUrl: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alternative_offers")
    var alternativeOffers: List<Offer> = emptyList()
    var note: String? = null

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    companion object : PanacheCompanionBase<SearchItem, UUID> {
        fun ofSearch(searchId: UUID): List<SearchItem> = list("searchId = ?1 order by itemNumber", searchId)
        fun nextNumber(searchId: UUID): Int =
            (find("searchId = ?1 order by itemNumber desc", searchId).firstResult()?.itemNumber ?: 0) + 1
    }
}
