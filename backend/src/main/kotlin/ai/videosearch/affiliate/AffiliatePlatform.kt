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

@Entity
@Table(name = "affiliate_platforms")
class AffiliatePlatform : PanacheEntityBase {
    @Id
    var id: UUID = UUID.randomUUID()
    lateinit var slug: String

    @Column(name = "display_name")
    lateinit var displayName: String

    @Column(name = "logo_url")
    var logoUrl: String? = null

    @Column(name = "is_active")
    var isActive: Boolean = true

    @Column(name = "integration_type")
    var integrationType: String = "URL_TEMPLATE"

    @Column(name = "domain_regex")
    lateinit var domainRegex: String

    @Column(name = "search_strategy")
    var searchStrategy: String = "SHOPPING_DOMAIN"

    @Column(name = "search_domain")
    var searchDomain: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_credential_fields")
    var requiredCredentialFields: List<CredentialField> = emptyList()

    @Column(name = "affiliate_url_template")
    lateinit var affiliateUrlTemplate: String

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_params")
    var extraParams: Map<String, String> = emptyMap()

    @Column(name = "ios_url_scheme")
    var iosUrlScheme: String? = null

    @Column(name = "android_intent_package")
    var androidIntentPackage: String? = null

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null

    fun matches(url: String): Boolean = Regex(domainRegex, RegexOption.IGNORE_CASE).containsMatchIn(url)

    companion object : PanacheCompanionBase<AffiliatePlatform, UUID> {
        fun active(): List<AffiliatePlatform> = list("isActive", true)

        /** Descobre a loja de um link bruto. */
        fun forUrl(url: String): AffiliatePlatform? = active().firstOrNull { it.matches(url) }
    }
}

data class CredentialField(
    val field: String = "",
    val label: String = "",
    val required: Boolean = true,
)
