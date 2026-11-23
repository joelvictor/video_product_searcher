package ai.videosearch.media

import ai.videosearch.ai.ProductAnalyzer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Transcreve a fala do vídeo uma vez, para todos os lotes de quadros usarem como texto.
 * Preferência: whisper.cpp em CPU (local, sem custo); sem ele, a própria IA quando ela ouve áudio (Gemini).
 */
@ApplicationScoped
class Transcriber {
    private val log = Logger.getLogger(Transcriber::class.java)

    @Inject lateinit var analyzer: ProductAnalyzer
    @Inject lateinit var scanner: VideoScanner

    @ConfigProperty(name = "vps.whisper.bin", defaultValue = "whisper-cli")
    lateinit var whisperBin: String

    @ConfigProperty(name = "vps.whisper.model")
    lateinit var modelOpt: java.util.Optional<String>

    @ConfigProperty(name = "vps.whisper.language", defaultValue = "pt")
    lateinit var language: String

    @ConfigProperty(name = "vps.whisper.threads", defaultValue = "4")
    var threads: Int = 4

    val whisperAvailable: Boolean
        get() = modelOpt.orElse("").let { it.isNotBlank() && Files.isRegularFile(Path.of(it)) }

    fun transcribe(video: Path): String? {
        val dir = Files.createTempDirectory("vps-audio")
        try {
            val wav = scanner.extractAudio(video, dir.resolve("a.wav")) ?: return null
            if (whisperAvailable) {
                return runCatching { whisper(wav) }
                    .onFailure { log.warnf("whisper.cpp falhou: %s", it.message) }
                    .getOrNull()
            }
            val mp3 = dir.resolve("a.mp3")
            ProcessRunner.check(listOf(scanner.ffmpeg, "-y", "-v", "error", "-i", wav.toString(), "-b:a", "32k", mp3.toString()), Duration.ofMinutes(3))
            return runCatching { analyzer.transcribe(Files.readAllBytes(mp3), "audio/mp3").ifBlank { null } }
                .onFailure { if (it !is UnsupportedOperationException) log.warnf("transcrição pela IA falhou: %s", it.message) }
                .getOrNull()
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun whisper(wav: Path): String? {
        val out = ProcessRunner.check(
            listOf(whisperBin, "-m", modelOpt.get(), "-f", wav.toString(), "-l", language, "-t", threads.toString(), "-np"),
            Duration.ofMinutes(20),
        )
        return formatWhisper(out).ifBlank { null }
    }

    companion object {
        private val line = Regex("""^\[(\d{2}):(\d{2}):(\d{2})\.\d{3} --> [^\]]+]\s*(.*)$""")

        /** `[00:01:05.000 --> 00:01:08.000]  texto` vira `[01:05] texto`; linhas sem tempo são ignoradas. */
        fun formatWhisper(output: String): String = output.lines().mapNotNull { l ->
            val m = line.find(l.trim()) ?: return@mapNotNull null
            val (h, mi, s, text) = m.destructured
            if (text.isBlank()) return@mapNotNull null
            val seconds = h.toInt() * 3600 + mi.toInt() * 60 + s.toInt()
            "[${FrameTools.clock(seconds.toDouble())}] ${text.trim()}"
        }.joinToString("\n")
    }
}
