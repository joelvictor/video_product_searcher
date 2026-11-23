package ai.videosearch.pipeline

import ai.videosearch.storage.MediaStorage
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.Duration
import java.time.OffsetDateTime

/**
 * O vídeo original fica guardado só pelo tempo de `vps.media.retention` (padrão 24 h), para o modo manual.
 * Depois é apagado; quadros e recortes (pequenos) ficam para o resultado continuar com imagens.
 */
@ApplicationScoped
class MediaCleanup {
    private val log = Logger.getLogger(MediaCleanup::class.java)

    @Inject lateinit var storage: MediaStorage

    @ConfigProperty(name = "vps.media.retention", defaultValue = "24h")
    lateinit var retention: Duration

    @Scheduled(every = "30m", delayed = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun run() {
        val before = OffsetDateTime.now().minus(retention)
        val expired = QuarkusTransaction.requiringNew().call { Search.withExpiredMedia(before).map { it.id to it.videoKey } }
        expired.forEach { (id, key) ->
            runCatching { key?.let(storage::delete) }.onFailure { log.warnf("não apagou o vídeo da busca %s: %s", id, it.message) }
            QuarkusTransaction.requiringNew().run { Search.findById(id)?.mediaDeleted = true }
        }
        if (expired.isNotEmpty()) log.infof("vídeos apagados por retenção: %d", expired.size)
    }
}
