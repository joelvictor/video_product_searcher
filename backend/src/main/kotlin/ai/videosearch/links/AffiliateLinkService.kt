package ai.videosearch.links

import ai.videosearch.affiliate.AffiliateLinkEngine
import ai.videosearch.affiliate.AffiliatePlatform
import ai.videosearch.affiliate.MissingMacroException
import ai.videosearch.affiliate.StoreCredential
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.SecureRandom

class LinkException(message: String) : RuntimeException(message)

data class GeneratedLink(
    val shortLink: ShortLink,
    val shortUrl: String,
    val affiliateUrl: String,
    val platform: AffiliatePlatform,
    val originalUrl: String,
)

/** Converte o link de uma loja no link afiliado da conta e cria o link curto (mesmo fluxo do StoryShop). */
@ApplicationScoped
class AffiliateLinkService {
    @Inject lateinit var expander: UrlExpander

    @ConfigProperty(name = "vps.public-base-url")
    lateinit var baseUrl: String

    private val random = SecureRandom()

    fun shortUrl(code: String) = baseUrl.trimEnd('/') + "/l/$code"

    @Transactional
    fun fromRawUrl(rawUrl: String): GeneratedLink {
        val expanded = runCatching { expander.expand(rawUrl) }.getOrElse { throw LinkException("URL inválida") }
        val platform = AffiliatePlatform.forUrl(expanded)
            ?: throw LinkException("Essa loja ainda não está cadastrada. Cadastre-a em Lojas.")
        return fromPlatformUrl(platform, expanded)
    }

    @Transactional
    fun fromPlatformUrl(platform: AffiliatePlatform, originalUrl: String): GeneratedLink {
        val cred = StoreCredential.findById(platform.id)?.takeIf { it.isEnabled }
            ?: throw LinkException("Configure o ID de afiliado da ${platform.displayName} em Lojas.")
        val affiliateUrl = try {
            AffiliateLinkEngine.build(platform.affiliateUrlTemplate, originalUrl, cred.values, platform.extraParams)
        } catch (e: MissingMacroException) {
            throw LinkException("Falta preencher '${e.macro.removePrefix("influencer.")}' da ${platform.displayName}.")
        }
        val link = ShortLink().also {
            it.code = newCode()
            it.platformId = platform.id
            it.affiliateUrl = affiliateUrl
            it.persist()
        }
        return GeneratedLink(link, shortUrl(link.code), affiliateUrl, platform, originalUrl)
    }

    private fun newCode(): String {
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        while (true) {
            val code = (1..7).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            if (ShortLink.byCode(code) == null) return code
        }
    }
}
