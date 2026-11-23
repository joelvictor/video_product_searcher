package ai.videosearch.media

import ai.videosearch.links.UrlExpander
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

class DownloadException(message: String) : RuntimeException(message)

class DownloadedVideo(val file: Path, val title: String?, val description: String?)

/**
 * Baixa o vídeo de uma URL do jeito mais leve e seguro:
 * - redes sociais (lista de domínios configurável) pelo yt-dlp, como processo separado, sem shell, sem cookies nem login,
 *   sem arquivo de configuração, com limite de tamanho, de duração e de tempo, em diretório temporário próprio;
 * - link direto de vídeo (.mp4 etc.) por HTTP, seguindo redirects manualmente e bloqueando hosts internos (SSRF).
 */
@ApplicationScoped
class VideoDownloader {
    private val log = Logger.getLogger(VideoDownloader::class.java)
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(8)).build()

    @Inject lateinit var mapper: ObjectMapper

    @ConfigProperty(name = "vps.download.ytdlp-bin", defaultValue = "yt-dlp")
    lateinit var ytdlp: String

    @ConfigProperty(name = "vps.download.allowed-domains")
    lateinit var allowedDomains: List<String>

    @ConfigProperty(name = "vps.download.max-duration", defaultValue = "900")
    var maxDuration: Int = 900

    @ConfigProperty(name = "vps.download.max-bytes", defaultValue = "314572800")
    var maxBytes: Long = 314_572_800

    @ConfigProperty(name = "vps.download.timeout", defaultValue = "240s")
    lateinit var timeout: Duration

    fun download(rawUrl: String): DownloadedVideo {
        val uri = validate(rawUrl)
        return if (isSocial(uri.host, allowedDomains)) viaYtDlp(uri.toString()) else direct(uri)
    }

    private fun viaYtDlp(url: String): DownloadedVideo {
        val dir = Files.createTempDirectory("vps-dl")
        val cmd = listOf(
            ytdlp, "--ignore-config", "--no-playlist", "--no-cache-dir", "--no-progress", "--no-warnings",
            "--restrict-filenames", "--no-mtime", "--socket-timeout", "20", "--retries", "2",
            "--max-filesize", maxBytes.toString(),
            "--match-filter", "!is_live & (!duration | duration <= $maxDuration)",
            "-f", "bv*[height<=720]+ba/b[height<=720]/bv*+ba/b",
            "--merge-output-format", "mp4",
            "--write-info-json",
            "-o", dir.resolve("video.%(ext)s").toString(),
            "--", url,
        )
        // ambiente mínimo: só o necessário para rodar e para o proxy de saída, nada de segredos do backend
        val env = System.getenv().filterKeys { it in PASS_ENV || it.lowercase().endsWith("_proxy") } + mapOf("HOME" to dir.toString())
        val result = runCatching { ProcessRunner.run(cmd, timeout, dir, env) }
            .getOrElse { dir.toFile().deleteRecursively(); throw DownloadException("O download demorou demais.") }
        val file = Files.list(dir).use { s ->
            s.filter { p -> p.fileName.toString().let { it.startsWith("video.") && !it.endsWith(".json") && !it.endsWith(".part") } }.findFirst().orElse(null)
        }
        if (result.exitCode != 0 || file == null) {
            log.warnf("yt-dlp (%d): %s", result.exitCode, result.output.takeLast(500))
            dir.toFile().deleteRecursively()
            throw DownloadException(explain(result.output))
        }
        val info = runCatching { mapper.readTree(dir.resolve("video.info.json").toFile()) }.getOrNull()
        return DownloadedVideo(file, info?.path("title")?.asText(null), info?.path("description")?.asText(null))
    }

    private fun direct(start: URI): DownloadedVideo {
        var uri = start
        repeat(6) {
            val res = http.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("User-Agent", "Mozilla/5.0").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            val location = res.headers().firstValue("location").orElse(null)
            if (res.statusCode() in 300..399 && location != null) {
                res.body().close()
                uri = validate(uri.resolve(location).toString())
                return@repeat
            }
            if (res.statusCode() !in 200..299) { res.body().close(); throw DownloadException("O link respondeu ${res.statusCode()}.") }
            val type = res.headers().firstValue("content-type").orElse("")
            if (!type.startsWith("video/") && type != "application/octet-stream") {
                res.body().close()
                throw DownloadException("Esse link não é de um vídeo nem de um site suportado (${allowedDomains.joinToString(", ")}).")
            }
            val declared = res.headers().firstValueAsLong("content-length").orElse(-1)
            if (declared > maxBytes) { res.body().close(); throw DownloadException("O vídeo passa do limite de ${maxBytes / 1_048_576} MB.") }
            val file = Files.createTempFile("vps-direct", ".mp4")
            try {
                res.body().use { input -> Files.newOutputStream(file).use { out -> copyLimited(input, out, maxBytes) } }
            } catch (e: Exception) {
                // download recusado ou interrompido não pode deixar o arquivo parcial ocupando disco
                Files.deleteIfExists(file)
                throw e
            }
            return DownloadedVideo(file, null, null)
        }
        throw DownloadException("Redirecionamentos demais.")
    }

    private fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream, limit: Long) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw DownloadException("O vídeo passa do limite de ${limit / 1_048_576} MB.")
            out.write(buf, 0, n)
        }
    }

    companion object {
        private val PASS_ENV = setOf("PATH", "LANG", "LC_ALL", "TZ", "SSL_CERT_FILE", "REQUESTS_CA_BUNDLE", "NODE_EXTRA_CA_CERTS")

        /** Só http(s) público; recusa hosts internos (localhost, rede privada, metadados de nuvem). */
        fun validate(raw: String): URI {
            val uri = runCatching { URI(raw.trim()) }.getOrElse { throw DownloadException("URL inválida.") }
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) throw DownloadException("Use um link http ou https.")
            if (uri.userInfo != null) throw DownloadException("URL inválida.")
            if (UrlExpander.isInternalHost(uri.host)) throw DownloadException("Host não permitido.")
            return uri
        }

        fun isSocial(host: String?, allowed: List<String>): Boolean {
            val h = host.orEmpty().lowercase().removePrefix("www.")
            return allowed.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { h == it || h.endsWith(".$it") }
        }

        /** Mensagem amigável a partir da saída do yt-dlp. */
        fun explain(output: String): String {
            val o = output.lowercase()
            return when {
                "does not pass filter" in o || "skipping" in o && "duration" in o -> "O vídeo é longo demais ou é uma transmissão ao vivo."
                "max-filesize" in o || "larger than max-filesize" in o || "file is larger" in o -> "O vídeo passa do limite de tamanho."
                "login" in o || "private" in o || "cookies" in o || "rate-limit" in o -> "Esse post é privado ou a rede pediu login. Baixe o vídeo e envie pelo upload."
                "unsupported url" in o -> "Esse link não é de um vídeo suportado."
                "not available" in o || "404" in o -> "O vídeo não está disponível (removido ou restrito)."
                else -> "Não foi possível baixar o vídeo desse link. Tente enviar o arquivo pelo upload."
            }
        }
    }
}
