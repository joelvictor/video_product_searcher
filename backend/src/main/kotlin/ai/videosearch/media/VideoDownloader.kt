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
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
            "--match-filter", matchFilter(maxDuration),
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

    /**
     * Link direto: o corpo é gravado por um subscriber assíncrono com limite de bytes, e a troca inteira
     * (redirects + corpo) tem o mesmo prazo do yt-dlp. Um servidor que manda os cabeçalhos e trava o corpo
     * não segura o worker: no prazo a requisição é cancelada.
     */
    internal fun direct(start: URI): DownloadedVideo {
        val deadline = System.nanoTime() + timeout.toNanos()
        var uri = start
        repeat(6) {
            val file = Files.createTempFile("vps-direct", ".mp4")
            val res = try {
                fetch(uri, file, deadline)
            } catch (e: Exception) {
                Files.deleteIfExists(file)
                throw e
            }
            val location = res.headers().firstValue("location").orElse(null)
            if (res.statusCode() in 300..399 && location != null) {
                Files.deleteIfExists(file)
                uri = validate(uri.resolve(location).toString())
                return@repeat
            }
            if (res.body() == null) {
                Files.deleteIfExists(file)
                throw DownloadException(rejection(res))
            }
            return DownloadedVideo(file, null, null)
        }
        throw DownloadException("Redirecionamentos demais.")
    }

    private fun fetch(uri: URI, file: Path, deadline: Long): HttpResponse<Path?> {
        val left = deadline - System.nanoTime()
        if (left <= 0) throw DownloadException("O download demorou demais.")
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("User-Agent", "Mozilla/5.0").GET().build()
        val handler = HttpResponse.BodyHandler<Path?> { info ->
            if (rejection(info) == null) LimitedFileSubscriber(file, maxBytes) else HttpResponse.BodySubscribers.replacing(null)
        }
        val future = http.sendAsync(request, handler)
        return try {
            future.get(left, TimeUnit.NANOSECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw DownloadException("O download demorou demais.")
        } catch (e: ExecutionException) {
            val cause = generateSequence(e.cause) { it.cause }.firstOrNull { it is DownloadException }
            throw cause as? DownloadException ?: DownloadException("Não foi possível baixar o vídeo desse link.")
        }
    }

    /** Motivo para não gravar o corpo da resposta, ou null quando é um vídeo aceitável. */
    private fun rejection(info: HttpResponse.ResponseInfo): String? = rejection(info.statusCode(), info.headers())

    private fun rejection(res: HttpResponse<*>): String = rejection(res.statusCode(), res.headers()) ?: "Não foi possível baixar o vídeo desse link."

    private fun rejection(status: Int, headers: java.net.http.HttpHeaders): String? {
        if (status in 300..399 && headers.firstValue("location").isPresent) return "redirect"
        if (status !in 200..299) return "O link respondeu $status."
        val type = headers.firstValue("content-type").orElse("")
        if (!type.startsWith("video/") && type != "application/octet-stream") {
            return "Esse link não é de um vídeo nem de um site suportado (${allowedDomains.joinToString(", ")})."
        }
        if (headers.firstValueAsLong("content-length").orElse(-1) > maxBytes) return "O vídeo passa do limite de ${maxBytes / 1_048_576} MB."
        return null
    }

    /** Grava o corpo em arquivo e cancela a resposta assim que passa do limite. */
    private class LimitedFileSubscriber(private val file: Path, private val limit: Long) : HttpResponse.BodySubscriber<Path?> {
        private val result = CompletableFuture<Path?>()
        private val channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        private lateinit var subscription: Flow.Subscription
        private var total = 0L

        override fun getBody(): CompletionStage<Path?> = result

        override fun onSubscribe(s: Flow.Subscription) {
            subscription = s
            s.request(1)
        }

        override fun onNext(item: List<ByteBuffer>) {
            try {
                for (b in item) {
                    total += b.remaining()
                    if (total > limit) throw DownloadException("O vídeo passa do limite de ${limit / 1_048_576} MB.")
                    while (b.hasRemaining()) channel.write(b)
                }
                subscription.request(1)
            } catch (e: Exception) {
                subscription.cancel()
                onError(e)
            }
        }

        override fun onError(t: Throwable) {
            runCatching { channel.close() }
            result.completeExceptionally(t)
        }

        override fun onComplete() {
            runCatching { channel.close() }
            result.complete(file)
        }
    }

    companion object {
        private val PASS_ENV = setOf("PATH", "LANG", "LC_ALL", "TZ", "SSL_CERT_FILE", "REQUESTS_CA_BUNDLE", "NODE_EXTRA_CA_CERTS")

        /** Sem ao vivo e até N segundos; `<=?` deixa passar quando o site não informa a duração. O yt-dlp não aceita parênteses. */
        fun matchFilter(maxDuration: Int) = "!is_live & duration <=? $maxDuration"

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
                "sign in to confirm" in o || "not a bot" in o -> "O YouTube bloqueou o download automático deste servidor (pede confirmação de que não é robô). Baixe o vídeo e envie pelo upload."
                "login" in o || "private" in o || "cookies" in o || "rate-limit" in o -> "Esse post é privado ou a rede pediu login. Baixe o vídeo e envie pelo upload."
                "unsupported url" in o -> "Esse link não é de um vídeo suportado."
                "not available" in o || "404" in o -> "O vídeo não está disponível (removido ou restrito)."
                else -> "Não foi possível baixar o vídeo desse link. Tente enviar o arquivo pelo upload."
            }
        }
    }
}
