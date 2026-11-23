package ai.videosearch.pipeline

import ai.videosearch.links.LinkException
import ai.videosearch.media.DownloadException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** Erros de validação viram 400/404/409 com `{ "error": "mensagem" }`, que o painel mostra como está. */
@Provider
class ApiErrorMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(e: RuntimeException): Response {
        if (e is jakarta.ws.rs.WebApplicationException) return e.response
        val status = when (e) {
            is NoSuchElementException -> 404
            is IllegalStateException -> 409
            is IllegalArgumentException, is DownloadException, is LinkException -> 400
            else -> return Response.serverError().entity(mapOf("error" to "Erro interno")).type("application/json").build()
        }
        return Response.status(status).entity(mapOf("error" to (e.message ?: "Requisição inválida"))).type("application/json").build()
    }
}
