package ai.videosearch.media

/** Métricas de um quadro amostrado (calculadas em CPU, sem IA). */
data class FrameStats(val time: Double, val sharpness: Double, val hash: Long)

/**
 * Escolhe quais quadros do vídeo inteiro vão para a IA:
 * 1. divide a linha do tempo em cenas quando o dHash muda muito de um quadro para o seguinte;
 * 2. descarta quadros borrados (bem abaixo da nitidez mediana do vídeo), mas toda cena mantém ao menos o seu melhor quadro;
 * 3. dentro de cada cena, pega os mais nítidos que não são quase iguais entre si;
 * 4. distribui o orçamento em rodadas: primeiro o melhor quadro de cada cena, depois o segundo, e assim por diante.
 */
object FrameSelector {
    data class Params(
        val budget: Int,
        val sceneThreshold: Int = 18,
        val duplicateThreshold: Int = 8,
        val maxPerScene: Int = 4,
        val blurRatio: Double = 0.3,
    )

    /** Orçamento de quadros: cresce com a duração, com piso e teto configuráveis. */
    fun budgetFor(durationSeconds: Double, secondsPerFrame: Double, maxFrames: Int, minFrames: Int = 6): Int =
        kotlin.math.ceil(durationSeconds / secondsPerFrame).toInt().coerceAtLeast(minFrames).coerceAtMost(maxFrames)

    fun scenes(frames: List<FrameStats>, threshold: Int): List<List<Int>> {
        if (frames.isEmpty()) return emptyList()
        val scenes = mutableListOf(mutableListOf(0))
        for (i in 1 until frames.size) {
            if (FrameTools.hamming(frames[i - 1].hash, frames[i].hash) >= threshold) scenes += mutableListOf(i) else scenes.last() += i
        }
        return scenes
    }

    /** Índices escolhidos, em ordem de tempo. */
    fun select(frames: List<FrameStats>, p: Params): List<Int> {
        if (frames.isEmpty() || p.budget <= 0) return emptyList()
        val median = frames.map { it.sharpness }.sorted()[frames.size / 2]
        val floor = median * p.blurRatio
        val perScene = scenes(frames, p.sceneThreshold).map { scene ->
            val ordered = scene.sortedByDescending { frames[it].sharpness }
            val picked = mutableListOf<Int>()
            for (i in ordered) {
                if (picked.size >= p.maxPerScene) break
                if (picked.isNotEmpty() && frames[i].sharpness < floor) continue
                if (picked.all { FrameTools.hamming(frames[it].hash, frames[i].hash) >= p.duplicateThreshold }) picked += i
            }
            picked
        }
        val chosen = mutableListOf<Int>()
        var round = 0
        while (chosen.size < p.budget) {
            val candidates = perScene.mapNotNull { it.getOrNull(round) }
            if (candidates.isEmpty()) break
            val room = p.budget - chosen.size
            chosen += if (candidates.size <= room) candidates else spread(candidates, room)
            round++
        }
        return chosen.sorted()
    }

    /** [k] itens espalhados de forma uniforme (mantém a cobertura do vídeo todo quando há mais cenas que orçamento). */
    fun <T> spread(items: List<T>, k: Int): List<T> {
        if (k <= 0) return emptyList()
        if (items.size <= k) return items
        return (0 until k).map { items[(it * items.size + items.size / (2 * k)) / k] }
    }
}
