package ai.videosearch.media

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.stream.IntStream
import javax.imageio.ImageIO

class SelectedFrame(val time: Double, val jpeg: ByteArray, val sharpness: Double)

class ScanResult(val frames: List<SelectedFrame>, val sampled: Int, val durationSeconds: Double)

/**
 * Varredura do vídeo inteiro em CPU: o ffmpeg amostra a linha do tempo toda (padrão 2 quadros/s), cada quadro recebe
 * nitidez e dHash (calculados em paralelo nos núcleos da máquina) e o [FrameSelector] escolhe os quadros por cena.
 */
@ApplicationScoped
class VideoScanner {
    private val log = Logger.getLogger(VideoScanner::class.java)

    @ConfigProperty(name = "vps.ffmpeg.bin", defaultValue = "ffmpeg")
    lateinit var ffmpeg: String

    @ConfigProperty(name = "vps.ffprobe.bin", defaultValue = "ffprobe")
    lateinit var ffprobe: String

    @ConfigProperty(name = "vps.scan.sample-fps", defaultValue = "2")
    var sampleFps: Double = 2.0

    @ConfigProperty(name = "vps.scan.max-frames", defaultValue = "60")
    var maxFrames: Int = 60

    @ConfigProperty(name = "vps.scan.seconds-per-frame", defaultValue = "2.5")
    var secondsPerFrame: Double = 2.5

    fun duration(video: Path): Double {
        val out = ProcessRunner.check(
            listOf(ffprobe, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", video.toString()),
            Duration.ofSeconds(30),
        )
        return out.lines().firstNotNullOfOrNull { it.trim().toDoubleOrNull() } ?: error("não foi possível ler a duração do vídeo")
    }

    fun hasVideoStream(video: Path): Boolean = runCatching {
        ProcessRunner.check(
            listOf(ffprobe, "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=codec_type", "-of", "csv=p=0", video.toString()),
            Duration.ofSeconds(30),
        ).contains("video")
    }.getOrDefault(false)

    fun scan(video: Path): ScanResult {
        val duration = duration(video)
        val dir = Files.createTempDirectory("vps-scan")
        try {
            val timeout = Duration.ofSeconds((120 + duration * 2).toLong())
            ProcessRunner.check(
                listOf(
                    ffmpeg, "-y", "-v", "error", "-i", video.toString(),
                    "-vf", "fps=$sampleFps,scale='min(960,iw)':-2", "-q:v", "3", dir.resolve("f%05d.jpg").toString(),
                ),
                timeout,
            )
            val files = Files.list(dir).use { s -> s.filter { it.fileName.toString().startsWith("f") }.sorted().toList() }
            check(files.isNotEmpty()) { "não foi possível extrair quadros do vídeo" }

            // métricas numa escala reduzida e fixa: rápidas e comparáveis entre vídeos de resoluções diferentes
            val stats = arrayOfNulls<FrameStats>(files.size)
            IntStream.range(0, files.size).parallel().forEach { i ->
                val small = FrameTools.downscale(ImageIO.read(files[i].toFile()), 320)
                stats[i] = FrameStats(i / sampleFps, FrameTools.sharpness(small), FrameTools.dHash(small))
            }
            val all = stats.map { it!! }
            val budget = FrameSelector.budgetFor(duration, secondsPerFrame, maxFrames)
            val picked = FrameSelector.select(all, FrameSelector.Params(budget = budget))
            log.infof("varredura: %.1fs de vídeo, %d quadros amostrados, %d escolhidos", duration, all.size, picked.size)
            return ScanResult(picked.map { SelectedFrame(all[it].time, Files.readAllBytes(files[it]), all[it].sharpness) }, all.size, duration)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** Extrai quadros nos tempos pedidos (modo manual), em resolução cheia (até 1280 px de largura). */
    fun framesAt(video: Path, times: List<Double>): List<ByteArray> {
        val dir = Files.createTempDirectory("vps-at")
        try {
            return times.mapIndexed { i, t ->
                val out = dir.resolve("a$i.jpg")
                ProcessRunner.check(
                    listOf(
                        ffmpeg, "-y", "-v", "error", "-ss", "%.3f".format(java.util.Locale.ROOT, t.coerceAtLeast(0.0)), "-i", video.toString(),
                        "-frames:v", "1", "-vf", "scale='min(1280,iw)':-2", "-q:v", "2", out.toString(),
                    ),
                    Duration.ofSeconds(60),
                )
                check(Files.exists(out)) { "não há quadro em ${FrameTools.clock(t)}" }
                Files.readAllBytes(out)
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** Áudio mono 16 kHz: WAV para o whisper.cpp e MP3 leve para o Gemini. Vídeo sem áudio devolve null. */
    fun extractAudio(video: Path, out: Path): Path? = runCatching {
        ProcessRunner.check(
            listOf(ffmpeg, "-y", "-v", "error", "-i", video.toString(), "-vn", "-ac", "1", "-ar", "16000", out.toString()),
            Duration.ofMinutes(5),
        )
        out.takeIf { Files.exists(it) && Files.size(it) > 4096 }
    }.getOrNull()
}
