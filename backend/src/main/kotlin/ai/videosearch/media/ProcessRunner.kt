package ai.videosearch.media

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class ProcessResult(val exitCode: Int, val output: String)

/** Executa um binário externo (ffmpeg, yt-dlp, whisper.cpp) sem shell, com tempo limite e ambiente controlado. */
object ProcessRunner {
    fun run(
        cmd: List<String>,
        timeout: Duration,
        workDir: Path? = null,
        env: Map<String, String>? = null,
    ): ProcessResult {
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        workDir?.let { pb.directory(it.toFile()) }
        if (env != null) pb.environment().apply { clear(); putAll(env) }
        val p = pb.start()
        val out = StringBuilder()
        val reader = Thread.ofVirtual().start {
            p.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (out.length < 200_000) out.appendLine(it) } }
        }
        if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
            error("${cmd.first()} excedeu o tempo limite de ${timeout.seconds}s")
        }
        reader.join(2000)
        return ProcessResult(p.exitValue(), out.toString())
    }

    fun check(cmd: List<String>, timeout: Duration, workDir: Path? = null, env: Map<String, String>? = null): String {
        val r = run(cmd, timeout, workDir, env)
        check(r.exitCode == 0) { "${cmd.first()} falhou: ${r.output.takeLast(400)}" }
        return r.output
    }

    fun available(bin: String): Boolean =
        runCatching { run(listOf(bin, "--help"), Duration.ofSeconds(10)).exitCode in 0..1 }.getOrDefault(false)
}
