package ai.videosearch.media

import ai.videosearch.support.ffmpegAvailable
import ai.videosearch.support.testVideo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class VideoScannerTest {
    private val scanner = VideoScanner().also {
        it.ffmpeg = "ffmpeg"
        it.ffprobe = "ffprobe"
        it.sampleFps = 2.0
        it.maxFrames = 60
        it.secondsPerFrame = 1.0
    }

    @Test
    fun `varre o video inteiro e escolhe quadros das duas cenas`() {
        assumeTrue(ffmpegAvailable(), "ffmpeg ausente")
        val video = testVideo(8)
        try {
            assertEquals(8.0, scanner.duration(video), 0.3)
            assertTrue(scanner.hasVideoStream(video))
            val r = scanner.scan(video)
            assertTrue(r.sampled >= 15, "amostrou ${r.sampled}")
            assertTrue(r.frames.isNotEmpty())
            assertEquals(r.frames.map { it.time }.sorted(), r.frames.map { it.time })
            assertTrue(r.frames.any { it.time < 4 } && r.frames.any { it.time >= 4 }, "cobre as duas cenas: ${r.frames.map { it.time }}")
            assertTrue(r.frames.size <= 8)
        } finally {
            video.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extrai quadros em tempos pedidos e o audio`() {
        assumeTrue(ffmpegAvailable(), "ffmpeg ausente")
        val video = testVideo(4)
        try {
            val frames = scanner.framesAt(video, listOf(0.5, 3.0))
            assertEquals(2, frames.size)
            assertEquals(360, FrameTools.read(frames[0]).width)
            val wav = scanner.extractAudio(video, video.parent.resolve("a.wav"))
            assertTrue(wav != null && Files.size(wav) > 4096)
        } finally {
            video.parent.toFile().deleteRecursively()
        }
    }
}
