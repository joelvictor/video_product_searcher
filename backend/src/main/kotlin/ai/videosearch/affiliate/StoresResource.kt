package ai.videosearch.affiliate

import jakarta.transaction.Transactional
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.time.OffsetDateTime
import java.util.UUID

data class StoreView(
    val id: UUID,
    val slug: String,
    val displayName: String,
    val domainRegex: String,
    val searchDomain: String?,
    val affiliateUrlTemplate: String,
    val requiredCredentialFields: List<CredentialField>,
    val extraParams: Map<String, String>,
    val androidIntentPackage: String?,
    val isActive: Boolean,
    val values: Map<String, String>,
    val enabled: Boolean,
    val priority: Int,
    val configured: Boolean,
)

data class CredentialInput(val values: Map<String, String> = emptyMap(), val enabled: Boolean = true, val priority: Int = 100)

data class PlatformInput(
    val slug: String = "",
    val displayName: String = "",
    val domainRegex: String = "",
    val searchDomain: String? = null,
    val affiliateUrlTemplate: String = "",
    val requiredCredentialFields: List<CredentialField> = emptyList(),
    val extraParams: Map<String, String> = emptyMap(),
    val androidIntentPackage: String? = null,
    val isActive: Boolean = true,
)

/** Lojas parceiras e as credenciais de afiliado da conta (o painel "Minhas lojas" + "Lojas (admin)" do StoryShop numa tela só). */
@Path("/api/stores")
@Produces(MediaType.APPLICATION_JSON)
class StoresResource {
    @GET
    @Transactional
    fun list(): List<StoreView> {
        val creds = StoreCredential.listAll().associateBy { it.platformId }
        return AffiliatePlatform.list("order by displayName").map { view(it, creds[it.id]) }
    }

    @PUT
    @Path("/{id}/credential")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    fun saveCredential(@PathParam("id") id: UUID, input: CredentialInput): StoreView {
        val p = AffiliatePlatform.findById(id) ?: throw NotFoundException()
        val allowed = p.requiredCredentialFields.map { it.field }.toSet()
        val cred = StoreCredential.findById(id) ?: StoreCredential().also { it.platformId = id; it.persist() }
        cred.values = input.values.filterKeys { it in allowed }.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        cred.isEnabled = input.enabled
        cred.priority = input.priority
        cred.updatedAt = OffsetDateTime.now()
        return view(p, cred)
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    fun create(input: PlatformInput): StoreView {
        if (AffiliatePlatform.count("slug", input.slug.trim()) > 0) throw BadRequestException("Já existe uma loja com esse identificador.")
        val p = AffiliatePlatform().also { apply(it, input); it.persist() }
        return view(p, null)
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    fun update(@PathParam("id") id: UUID, input: PlatformInput): StoreView {
        val p = AffiliatePlatform.findById(id) ?: throw NotFoundException()
        apply(p, input)
        return view(p, StoreCredential.findById(id))
    }

    private fun apply(p: AffiliatePlatform, i: PlatformInput) {
        if (i.slug.isBlank() || i.displayName.isBlank() || i.domainRegex.isBlank() || i.affiliateUrlTemplate.isBlank()) {
            throw BadRequestException("Preencha identificador, nome, regex de domínio e template do link.")
        }
        runCatching { Regex(i.domainRegex) }.getOrElse { throw BadRequestException("Regex de domínio inválida.") }
        p.slug = i.slug.trim()
        p.displayName = i.displayName.trim()
        p.domainRegex = i.domainRegex.trim()
        p.searchDomain = i.searchDomain?.trim()?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')?.ifBlank { null }
        p.affiliateUrlTemplate = i.affiliateUrlTemplate.trim()
        p.requiredCredentialFields = i.requiredCredentialFields.filter { it.field.isNotBlank() }
        p.extraParams = i.extraParams
        p.androidIntentPackage = i.androidIntentPackage?.trim()?.ifBlank { null }
        p.isActive = i.isActive
    }

    private fun view(p: AffiliatePlatform, c: StoreCredential?): StoreView {
        val values = c?.values ?: emptyMap()
        val configured = p.requiredCredentialFields.filter { it.required }.all { !values[it.field].isNullOrBlank() }
        return StoreView(
            p.id, p.slug, p.displayName, p.domainRegex, p.searchDomain, p.affiliateUrlTemplate, p.requiredCredentialFields,
            p.extraParams, p.androidIntentPackage, p.isActive, values, c?.isEnabled ?: false, c?.priority ?: 100, configured,
        )
    }
}
