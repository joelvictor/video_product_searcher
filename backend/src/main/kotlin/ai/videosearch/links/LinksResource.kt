package ai.videosearch.links

import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

data class LinkRequest(val url: String = "")
data class CreatedLink(val shortUrl: String, val affiliateUrl: String, val platform: String)

/** Gera o link afiliado a partir de um link de loja colado (o "Gerar link" do StoryShop). */
@Path("/api/links")
@Produces(MediaType.APPLICATION_JSON)
class LinksResource {
    @Inject lateinit var service: AffiliateLinkService

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    fun create(req: LinkRequest): CreatedLink {
        val l = service.fromRawUrl(req.url)
        return CreatedLink(l.shortUrl, l.affiliateUrl, l.platform.displayName)
    }
}
