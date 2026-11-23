package ai.videosearch.links

import ai.videosearch.affiliate.AffiliatePlatform
import jakarta.transaction.Transactional
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import java.net.URI

/**
 * Link curto `/l/{code}`. No Android dentro do navegador do Instagram/TikTok,
 * usa `intent://` para tentar abrir o app da loja; nos demais casos faz 302 para a
 * URL afiliada, e os universal links da própria loja cuidam de abrir o app.
 */
@Path("/l")
class RedirectResource {

    @GET
    @Path("/{code}")
    @Transactional
    fun redirect(@PathParam("code") code: String, @HeaderParam("User-Agent") ua: String?): Response {
        val link = ShortLink.byCode(code) ?: throw NotFoundException()
        link.clickCount += 1

        val platform = link.platformId?.let { AffiliatePlatform.findById(it) }
        val isAndroid = ua?.contains("Android") == true
        val pkg = platform?.androidIntentPackage
        if (isAndroid && pkg != null && InAppBrowser.detect(ua) != null) {
            return Response.status(Response.Status.FOUND).location(URI(androidIntent(link.affiliateUrl, pkg))).build()
        }
        return Response.status(Response.Status.FOUND).location(URI(link.affiliateUrl)).build()
    }

    private fun androidIntent(url: String, pkg: String): String {
        val uri = URI(url)
        val fallback = java.net.URLEncoder.encode(url, Charsets.UTF_8)
        return "intent://${uri.rawAuthority}${uri.rawPath.orEmpty()}${uri.rawQuery?.let { "?$it" }.orEmpty()}" +
            "#Intent;scheme=https;package=$pkg;S.browser_fallback_url=$fallback;end"
    }
}
