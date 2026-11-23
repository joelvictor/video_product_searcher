package ai.videosearch.pipeline

import ai.videosearch.ai.DeepSeekClient
import ai.videosearch.media.Transcriber
import ai.videosearch.media.VideoDownloader
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import ai.videosearch.settings.RuntimeSettings
import ai.videosearch.settings.Setting

data class AppConfig(
    val aiProvider: String,
    val searchProvider: String,
    /** Falta a chave do provedor escolhido (o painel avisa e leva para Configurações). */
    val aiConfigured: Boolean,
    val searchConfigured: Boolean,
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

    @Inject lateinit var settings: RuntimeSettings
    @Inject lateinit var deepseek: DeepSeekClient

    @GET
    fun get(): AppConfig {
        val ai = settings.get(Setting.AI_PROVIDER).trim().lowercase()
        val search = settings.get(Setting.SEARCH_PROVIDER).trim().lowercase()
        return AppConfig(
            ai,
            search,
            aiConfigured = if (ai == "gemini") settings.get(Setting.AI_GEMINI_KEY).isNotEmpty() else deepseek.analysis.configured,
            searchConfigured = when (search) {
                "serpapi" -> settings.get(Setting.SEARCH_SERPAPI_KEY).isNotEmpty()
                "brave" -> settings.get(Setting.SEARCH_BRAVE_KEY).isNotEmpty()
                else -> deepseek.search.configured
            },
            transcription = when {
                transcriber.whisperAvailable -> "whisper.cpp (CPU)"
                ai == "gemini" -> "gemini"
                else -> "desligada"
            },
            socialDomains = downloader.allowedDomains,
            maxDurationSeconds = downloader.maxDuration,
            maxManualFrames = SearchPipeline.MAX_MANUAL_FRAMES,
        )
    }
}
