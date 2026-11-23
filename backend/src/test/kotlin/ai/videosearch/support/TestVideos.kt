package ai.videosearch.support

import java.nio.file.Files
import java.nio.file.Path

fun ffmpegAvailable() = runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0 }.getOrDefault(false)

/** Vídeo de teste com duas cenas bem diferentes (padrão de teste e fractal) e áudio. */
fun testVideo(seconds: Int = 6): Path {
    val dir = Files.createTempDirectory("vps-test-video")
    val file = dir.resolve("v.mp4")
    val half = seconds / 2.0
    val p = ProcessBuilder(
        "ffmpeg", "-y", "-v", "error",
        "-f", "lavfi", "-i", "testsrc=duration=$half:size=360x640:rate=15",
        "-f", "lavfi", "-i", "mandelbrot=size=360x640:rate=15",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=$seconds",
        "-filter_complex", "[1:v]trim=duration=$half,setpts=PTS-STARTPTS[b];[0:v][b]concat=n=2:v=1:a=0[v]",
        "-map", "[v]", "-map", "2:a", "-shortest", "-pix_fmt", "yuv420p", file.toString(),
    ).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    check(p.waitFor() == 0) { out }
    return file
}
