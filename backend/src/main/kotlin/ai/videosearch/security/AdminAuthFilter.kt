package ai.videosearch.security

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.MessageDigest

/**
 * Primeira versão sem login: o painel é protegido por uma senha única (`VPS_ADMIN_PASSWORD`), enviada como
 * `Authorization: Bearer <senha>` em toda chamada a `/api`. Links curtos (`/l`) e mídia assinada (`/m`) ficam públicos.
 * Sem senha configurada o painel fica aberto (só para desenvolvimento local).
 */
@Provider
@PreMatching
class AdminAuthFilter : ContainerRequestFilter {
    @ConfigProperty(name = "vps.admin-password")
    lateinit var passwordOpt: java.util.Optional<String>

    override fun filter(ctx: ContainerRequestContext) {
        val path = ctx.uriInfo.path.trimStart('/')
        if (!path.startsWith("api/") || ctx.method == "OPTIONS") return
        val password = passwordOpt.orElse("")
        if (password.isEmpty()) return
        val given = ctx.getHeaderString("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
        if (!MessageDigest.isEqual(given.toByteArray(), password.toByteArray())) {
            ctx.abortWith(Response.status(401).entity(mapOf("error" to "Senha do painel inválida")).type("application/json").build())
        }
    }
}
