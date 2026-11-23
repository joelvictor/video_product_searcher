package ai.videosearch.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameSelectorTest {
    // hashes bem diferentes entre cenas (todos os bits trocados) e quase iguais dentro da cena
    private val sceneA = 0L
    private val sceneB = -1L
    private val sceneC = 0x00000000FFFFFFFFL

    private fun f(t: Double, sharp: Double, hash: Long) = FrameStats(t, sharp, hash)

    @Test
    fun `divide em cenas quando o hash muda muito`() {
        val frames = listOf(f(0.0, 10.0, sceneA), f(0.5, 10.0, sceneA or 1), f(1.0, 10.0, sceneB), f(1.5, 10.0, sceneC))
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), FrameSelector.scenes(frames, 18))
    }

    @Test
    fun `cada cena entra com o quadro mais nitido antes de qualquer segundo quadro`() {
        val frames = listOf(
            f(0.0, 5.0, sceneA), f(0.5, 50.0, sceneA or 0xFFF), f(1.0, 40.0, sceneA),
            f(1.5, 30.0, sceneB), f(2.0, 31.0, sceneB),
            f(2.5, 20.0, sceneC),
        )
        val picked = FrameSelector.select(frames, FrameSelector.Params(budget = 3))
        assertEquals(listOf(1, 4, 5), picked)
    }

    @Test
    fun `quadros quase iguais da mesma cena nao se repetem e borrados ficam de fora`() {
        val frames = listOf(f(0.0, 100.0, sceneA), f(0.5, 99.0, sceneA or 1), f(1.0, 1.0, sceneA or 0xFFF), f(1.5, 90.0, sceneA or 0xFF00))
        val picked = FrameSelector.select(frames, FrameSelector.Params(budget = 10))
        // 1 é quase igual ao 0; 2 é borrado; 3 é diferente o bastante
        assertEquals(listOf(0, 3), picked)
    }

    @Test
    fun `com mais cenas que orcamento os quadros cobrem o video inteiro`() {
        val frames = (0 until 20).map { i -> f(i.toDouble(), 10.0, if (i % 2 == 0) sceneA else sceneB) }
        val picked = FrameSelector.select(frames, FrameSelector.Params(budget = 4))
        assertEquals(4, picked.size)
        assertTrue(picked.first() < 5 && picked.last() > 14, "espalhados: $picked")
    }

    @Test
    fun `orcamento cresce com a duracao dentro do piso e do teto`() {
        assertEquals(6, FrameSelector.budgetFor(5.0, 2.5, 60))
        assertEquals(24, FrameSelector.budgetFor(60.0, 2.5, 60))
        assertEquals(60, FrameSelector.budgetFor(600.0, 2.5, 60))
    }
}
