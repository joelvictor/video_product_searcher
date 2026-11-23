package ai.videosearch.affiliate

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

/** Credenciais de afiliado da conta do painel em cada loja (no StoryShop eram por criador). */
@Entity
@Table(name = "store_credentials")
class StoreCredential : PanacheEntityBase {
    @Id
    @Column(name = "platform_id")
    lateinit var platformId: UUID

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credential_values")
    var values: Map<String, String> = emptyMap()

    @Column(name = "is_enabled")
    var isEnabled: Boolean = true
    var priority: Int = 100

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()

    companion object : PanacheCompanionBase<StoreCredential, UUID> {
        fun enabled(): List<StoreCredential> = list("isEnabled = true order by priority")
    }
}
