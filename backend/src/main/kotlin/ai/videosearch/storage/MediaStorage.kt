package ai.videosearch.storage

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/** Armazenamento de mídia por chave (disco local/volume, como no StoryShop). Uma implementação S3 entra atrás da mesma interface. */
interface MediaStorage {
    fun put(key: String, bytes: ByteArray)
    fun putFile(key: String, source: Path)
    fun get(key: String): ByteArray
    fun exists(key: String): Boolean
    /** Caminho local do arquivo (para ffmpeg e para servir vídeo com Range). */
    fun path(key: String): Path
    fun delete(key: String)
    fun deletePrefix(prefix: String)
}

@ApplicationScoped
class LocalMediaStorage : MediaStorage {
    @ConfigProperty(name = "vps.storage.dir")
    lateinit var dir: String

    override fun path(key: String): Path {
        val root = Paths.get(dir).toAbsolutePath().normalize()
        val p = root.resolve(key).normalize()
        require(p.startsWith(root) && p != root) { "chave inválida" }
        return p
    }

    override fun put(key: String, bytes: ByteArray) {
        val p = path(key)
        Files.createDirectories(p.parent)
        Files.write(p, bytes)
    }

    override fun putFile(key: String, source: Path) {
        val p = path(key)
        Files.createDirectories(p.parent)
        runCatching { Files.move(source, p, StandardCopyOption.REPLACE_EXISTING) }
            .getOrElse { Files.copy(source, p, StandardCopyOption.REPLACE_EXISTING) }
    }

    override fun get(key: String): ByteArray = Files.readAllBytes(path(key))
    override fun exists(key: String): Boolean = Files.isRegularFile(path(key))
    override fun delete(key: String) { Files.deleteIfExists(path(key)) }
    override fun deletePrefix(prefix: String) { path(prefix).toFile().deleteRecursively() }
}
