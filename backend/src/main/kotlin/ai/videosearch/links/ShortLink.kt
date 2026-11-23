package ai.videosearch.links

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "short_links")
class ShortLink : PanacheEntityBase {
    @Id
    var id: UUID = UUID.randomUUID()
    lateinit var code: String

    @Column(name = "platform_id")
    var platformId: UUID? = null

    @Column(name = "affiliate_url")
    lateinit var affiliateUrl: String

    @Column(name = "click_count")
    var clickCount: Long = 0

    companion object : PanacheCompanionBase<ShortLink, UUID> {
        fun byCode(code: String): ShortLink? = find("code", code).firstResult()
    }
}
