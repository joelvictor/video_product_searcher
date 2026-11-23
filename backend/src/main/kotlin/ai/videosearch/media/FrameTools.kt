package ai.videosearch.media

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Operações de imagem em CPU usadas na varredura (vindas do StoryShop: nitidez, dHash e recorte). */
object FrameTools {
    /** Variância do Laplaciano (filtro anti-borrão): maior = mais nítido. */
    fun sharpness(img: BufferedImage): Double {
        val w = img.width
        val h = img.height
        if (w < 3 || h < 3) return 0.0
        val gray = Array(h) { y -> IntArray(w) { x -> val c = img.getRGB(x, y); ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10 } }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val l = gray[y - 1][x] + gray[y + 1][x] + gray[y][x - 1] + gray[y][x + 1] - 4 * gray[y][x]
            sum += l
            sumSq += l.toDouble() * l
            n++
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** dHash de 64 bits: barato e robusto a recompressão; serve para achar trocas de cena e quadros repetidos. */
    fun dHash(image: BufferedImage): Long {
        val w = 9
        val h = 8
        val gray = Array(h) { y ->
            IntArray(w) { x ->
                val x0 = x * image.width / w
                val x1 = maxOf(x0 + 1, (x + 1) * image.width / w)
                val y0 = y * image.height / h
                val y1 = maxOf(y0 + 1, (y + 1) * image.height / h)
                val step = maxOf(1, minOf(x1 - x0, y1 - y0) / 16)
                var sum = 0L
                var n = 0
                for (sy in y0 until y1 step step) for (sx in x0 until x1 step step) {
                    val rgb = image.getRGB(sx, sy)
                    sum += ((rgb shr 16 and 0xff) * 299 + (rgb shr 8 and 0xff) * 587 + (rgb and 0xff) * 114) / 1000
                    n++
                }
                (sum / n).toInt()
            }
        }
        var hash = 0L
        var bit = 0
        for (y in 0 until h) for (x in 0 until w - 1) {
            if (gray[y][x] > gray[y][x + 1]) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Reduz para [maxWidth] de largura (as métricas são calculadas numa escala fixa, rápida e comparável). */
    fun downscale(img: BufferedImage, maxWidth: Int): BufferedImage {
        if (img.width <= maxWidth) return img
        val h = maxOf(1, img.height * maxWidth / img.width)
        return BufferedImage(maxWidth, h, BufferedImage.TYPE_INT_RGB).also {
            val g = it.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(img, 0, 0, maxWidth, h, null)
            g.dispose()
        }
    }

    fun read(jpeg: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(jpeg)) ?: error("imagem inválida")

    fun toJpeg(img: BufferedImage): ByteArray {
        val rgb = if (img.type == BufferedImage.TYPE_INT_RGB) img else BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).also {
            it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        }
        return ByteArrayOutputStream().also { ImageIO.write(rgb, "jpg", it) }.toByteArray()
    }

    /** Recorta [box] ([ymin, xmin, ymax, xmax] em 0..1000) com margem. Caixa inválida ou minúscula devolve o quadro inteiro. */
    fun crop(frameJpeg: ByteArray, box: List<Int>, margin: Double = 0.08): ByteArray {
        if (box.size != 4) return frameJpeg
        val src = read(frameJpeg)
        val (ymin, xmin, ymax, xmax) = box.map { it.coerceIn(0, 1000) }
        if (ymax <= ymin || xmax <= xmin) return frameJpeg
        val x0 = xmin * src.width / 1000
        val x1 = xmax * src.width / 1000
        val y0 = ymin * src.height / 1000
        val y1 = ymax * src.height / 1000
        val mx = ((x1 - x0) * margin).toInt()
        val my = ((y1 - y0) * margin).toInt()
        val cx0 = (x0 - mx).coerceAtLeast(0)
        val cy0 = (y0 - my).coerceAtLeast(0)
        val cx1 = (x1 + mx).coerceAtMost(src.width)
        val cy1 = (y1 + my).coerceAtMost(src.height)
        if (cx1 - cx0 < 8 || cy1 - cy0 < 8) return frameJpeg
        return toJpeg(src.getSubimage(cx0, cy0, cx1 - cx0, cy1 - cy0))
    }

    /** "mm:ss" (ou "h:mm:ss") para mostrar no prompt e no painel. */
    fun clock(seconds: Double): String {
        val s = seconds.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
