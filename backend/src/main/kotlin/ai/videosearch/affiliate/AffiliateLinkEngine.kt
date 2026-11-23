package ai.videosearch.affiliate

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Motor de conversão de links (RF-10/RF-11): aplica o template da plataforma
 * substituindo macros `{nome}` por, nesta ordem:
 *  - `{influencer.<campo>}`  → credenciais de afiliado preenchidas na tela Lojas (prefixo mantido do StoryShop para reaproveitar os templates);
 *  - variáveis derivadas da URL original (`original_url`, `encoded_original_url`,
 *    `original_path`, `asin`, `clean_url` e `encoded_clean_url`, que são a URL sem query nem fragmento);
 *  - `extra_params` da plataforma (ex.: `merchant_id` da Awin).
 *
 * Não depende de banco nem de CDI para poder ser testado isoladamente.
 */
object AffiliateLinkEngine {
    private val macro = Regex("\\{([a-zA-Z0-9_.]+)}")
    private val asinRegex = Regex("/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})")

    fun build(
        template: String,
        originalUrl: String,
        credentials: Map<String, String>,
        platformParams: Map<String, String> = emptyMap(),
    ): String {
        val vars = buildMap {
            putAll(platformParams)
            putAll(urlVariables(originalUrl))
            credentials.forEach { (k, v) -> put("influencer.$k", v) }
        }
        return macro.replace(template) { m ->
            val key = m.groupValues[1]
            vars[key] ?: throw MissingMacroException(key)
        }
    }

    fun urlVariables(originalUrl: String): Map<String, String> {
        val uri = URI(originalUrl.trim())
        return buildMap {
            put("original_url", originalUrl)
            put("encoded_original_url", URLEncoder.encode(originalUrl, StandardCharsets.UTF_8))
            put("original_path", uri.rawPath.orEmpty())
            val clean = "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.orEmpty()}"
            put("clean_url", clean)
            put("encoded_clean_url", URLEncoder.encode(clean, StandardCharsets.UTF_8))
            asinRegex.find(uri.rawPath.orEmpty())?.let { put("asin", it.groupValues[1]) }
        }
    }
}

class MissingMacroException(val macro: String) :
    IllegalArgumentException("Macro '{$macro}' sem valor: falta credencial da loja ou parâmetro da plataforma")
