package ai.videosearch.links

import jakarta.enterprise.context.ApplicationScoped
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Segue redirects de links encurtados (amzn.to, s.shopee.com.br, bit.ly…) até a URL final.
 * Só segue http(s) e bloqueia hosts internos para evitar SSRF.
 */
@ApplicationScoped
class UrlExpander {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(4))
        .build()

    fun expand(url: String): String {
        var current = url.trim()
        repeat(MAX_HOPS) {
            val uri = URI(current)
            require(uri.scheme in setOf("http", "https")) { "URL inválida" }
            require(!isInternalHost(uri.host)) { "Host não permitido" }
            val res = try {
                client.send(
                    HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", "Mozilla/5.0 (compatible; VideoProductSearcher/1.0)")
                        .timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
            } catch (e: Exception) {
                return current
            }
            val location = res.headers().firstValue("location").orElse(null)
            if (res.statusCode() !in 300..399 || location == null) return current
            current = uri.resolve(location).toString()
        }
        return current
    }

    companion object {
        private const val MAX_HOPS = 6

        fun isInternalHost(host: String?): Boolean {
            if (host == null) return true
            val h = host.lowercase()
            if (h == "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return true
            return runCatching {
                val a = java.net.InetAddress.getByName(h)
                a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress
            }.getOrDefault(true)
        }
    }
}
