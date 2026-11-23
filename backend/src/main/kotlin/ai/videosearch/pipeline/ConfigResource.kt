package ai.videosearch.pipeline

import ai.videosearch.media.Transcriber
import ai.videosearch.media.VideoDownloader
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty

data class AppConfig(
    val aiProvider: String,
    val searchProvider: String,
    val transcription: String,
    val socialDomains: List<String>,
    val maxDurationSeconds: Int,
    val maxManualFrames: Int,
)

/** O que o painel precisa saber da configuração (nunca devolve segredos). Também serve para validar a senha. */
@Path("/api/config")
@Produces(MediaType.APPLICATION_JSON)
class ConfigResource {
    @Inject lateinit var transcriber: Transcriber
    @Inject lateinit var downloader: VideoDownloader

    @ConfigProperty(name = "vps.ai.provider") lateinit var ai: String
    @ConfigProperty(name = "vps.search.provider") lateinit var search: String

    @GET
    fun get() = AppConfig(
        ai.trim().lowercase(),
        search.trim().lowercase(),
        when {
            transcriber.whisperAvailable -> "whisper.cpp (CPU)"
            ai.trim().lowercase() == "gemini" -> "gemini"
            else -> "desligada"
        },
        downloader.allowedDomains,
        downloader.maxDuration,
        SearchPipeline.MAX_MANUAL_FRAMES,
    )
}
