package ai.videosearch.media

import ai.videosearch.storage.MediaStorage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.CacheControl
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * URLs temporárias e assinadas para mídia privada: usadas pelo painel (vídeo, quadros e recortes, que a tag
 * `<video>`/`<img>` não consegue pedir com cabeçalho de senha) e pela busca visual (Google Lens exige URL pública).
 */
@ApplicationScoped
class SignedMediaUrls {
    /** Vazio = o backend gera uma chave aleatória e a guarda no volume de mídia (ver [key]). */
    @ConfigProperty(name = "vps.signing-key")
    lateinit var configuredKey: java.util.Optional<String>

    @Inject lateinit var storage: MediaStorage

    private val key: ByteArray by lazy {
        configuredKey.orElse("").trim().takeIf { it.isNotEmpty() }?.toByteArray() ?: generatedKey()
    }

    /** Chave derivada para outro uso (cifrar chaves de API), sem expor a chave de assinatura. */
    fun derivedKey(label: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(label.toByteArray())

    private fun generatedKey(): ByteArray {
        val name = ".signing-key"
        if (storage.exists(name)) return storage.get(name).also { require(it.isNotEmpty()) { "$name vazio" } }
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val encoded = Base64.getEncoder().encodeToString(bytes).toByteArray()
        storage.put(name, encoded)
        return encoded
    }

    @ConfigProperty(name = "vps.public-base-url")
    lateinit var baseUrl: String

    fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    /** Caminho relativo (`/m/...`), servido na mesma origem do painel. */
    fun path(key: String, ttlSeconds: Long = 6 * 3600): String {
        val exp = System.currentTimeMillis() / 1000 + ttlSeconds
        val k = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray())
        return "/m/$exp/$k/${sign("$exp|$key")}"
    }

    /** URL absoluta (para serviços externos como o Google Lens). */
    fun absolute(key: String, ttlSeconds: Long = 900) = baseUrl.trimEnd('/') + path(key, ttlSeconds)

    fun verify(exp: Long, encodedKey: String, sig: String): String? = runCatching {
        val key = String(Base64.getUrlDecoder().decode(encodedKey))
        if (exp < System.currentTimeMillis() / 1000) return null
        if (!MessageDigest.isEqual(sign("$exp|$key").toByteArray(), sig.toByteArray())) return null
        key
    }.getOrNull()
}

@Path("/m")
class SignedMediaResource {
    @Inject lateinit var urls: SignedMediaUrls
    @Inject lateinit var storage: MediaStorage

    /** Serve imagem ou vídeo; vídeo aceita `Range` para o player conseguir avançar na linha do tempo. */
    @GET
    @Path("/{exp}/{key}/{sig}")
    fun get(
        @PathParam("exp") exp: Long,
        @PathParam("key") key: String,
        @PathParam("sig") sig: String,
        @HeaderParam("Range") range: String?,
    ): Response {
        val storageKey = urls.verify(exp, key, sig) ?: throw NotFoundException()
        if (!storage.exists(storageKey)) throw NotFoundException()
        val file = storage.path(storageKey)
        val size = Files.size(file)
        val type = contentType(storageKey)
        val cache = CacheControl().apply { isPrivate = true; maxAge = 600 }
        val r = parseRange(range, size)
            ?: return Response.ok(StreamingOutput { out -> Files.newInputStream(file).use { it.transferTo(out) } }, type)
                .header("Accept-Ranges", "bytes").header("Content-Length", size).cacheControl(cache).build()
        val (start, end) = r
        val len = end - start + 1
        val body = StreamingOutput { out ->
            RandomAccessFile(file.toFile(), "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(64 * 1024)
                var left = len
                while (left > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    left -= n
                }
            }
        }
        return Response.status(206).entity(body).type(type)
            .header("Accept-Ranges", "bytes")
            .header("Content-Range", "bytes $start-$end/$size")
            .header("Content-Length", len)
            .cacheControl(cache).build()
    }

    companion object {
        fun contentType(key: String) = when (key.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }

        /** `bytes=a-b`, `bytes=a-` ou `bytes=-n`. Fora do arquivo ou malformado: null (resposta inteira). */
        fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
            val spec = header?.trim()?.removePrefix("bytes=")?.takeIf { header.trim().startsWith("bytes=") && !it.contains(',') } ?: return null
            val (a, b) = spec.split("-", limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
            val (start, end) = when {
                a.isEmpty() && b.isNotEmpty() -> (size - b.toLongOrNull().let { it ?: return null }).coerceAtLeast(0) to size - 1
                a.isNotEmpty() -> (a.toLongOrNull() ?: return null) to (b.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1))
                else -> return null
            }
            if (size == 0L || start > end || start >= size) return null
            return start to end
        }
    }
}
