package ai.videosearch.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Um quadro enviado à IA, com o rótulo que aparece no prompt (ex.: "Quadro 3 (00:42)"). */
class FrameInput(val jpeg: ByteArray, val label: String)

class AnalysisInput(
    val frames: List<FrameInput>,
    /** Áudio do trecho (só o Gemini ouve). */
    val audioMp3: ByteArray? = null,
    /** Transcrição da fala (whisper.cpp ou Gemini), com marcas de tempo. */
    val transcript: String? = null,
    /** Título/descrição do vídeo na rede social. */
    val caption: String? = null,
    /** Modo manual: o usuário escolheu estes quadros (e às vezes recortou a área do produto). */
    val userSelected: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DetectedProduct(
    val label: String = "",
    val brand: String? = null,
    val searchQuery: String = "",
    /** PRIMARY_FOCUS (assunto do vídeo) ou SCENE_ITEM (qualquer outro produto visível). */
    val layer: String = "PRIMARY_FOCUS",
    /** EXACT_BRANDED_PRODUCT (Rota A, texto) ou VISUAL_FASHION_ITEM (Rota B, imagem). */
    val detectionType: String = "EXACT_BRANDED_PRODUCT",
    val category: String? = null,
    /** Índice do quadro dentro do lote enviado. */
    val frameIndex: Int = 0,
    /** [ymin, xmin, ymax, xmax] em 0..1000. */
    val box: List<Int> = emptyList(),
    val confidence: Double = 0.0,
    val evidence: List<String> = emptyList(),
)

data class AnalysisResult(
    val items: List<DetectedProduct>,
    val spokenSummary: String? = null,
    val screenText: String? = null,
)

/** IA multimodal: um lote de quadros (+ áudio ou transcrição) numa chamada, devolvendo os produtos em camadas. */
interface ProductAnalyzer {
    fun analyze(input: AnalysisInput): AnalysisResult

    /** Transcreve áudio. Provedores sem áudio lançam [UnsupportedOperationException]. */
    fun transcribe(audio: ByteArray, mimeType: String): String
}

/** Texto comum aos provedores: o que é pedido à IA e o contexto do vídeo. */
object AnalysisPrompt {
    val RULES = """
        Você analisa vídeos de redes sociais (TikTok, Reels, Shorts, Stories) para encontrar produtos que podem ser comprados
        em lojas online com link de afiliado. Recebe quadros numerados do vídeo, com o tempo de cada um, e, quando houver,
        a transcrição da fala e a legenda do vídeo. Leia embalagens e textos na tela (podem estar espelhados).
        Liste TODOS os produtos comerciais visíveis ou citados: layer=PRIMARY_FOCUS quando o produto é assunto do vídeo
        (falado, segurado, apontado, em destaque) e layer=SCENE_ITEM para o resto (fundo, roupa, móveis).
        detection_type=EXACT_BRANDED_PRODUCT para itens com marca/modelo identificáveis (cosméticos, eletrônicos, livros, suplementos…)
        e VISUAL_FASHION_ITEM para moda, calçados, bolsas e decoração sem marca legível.
        search_query: consulta curta em português para achar o produto numa loja (marca + produto + variação).
        frame_index: número do quadro deste lote em que o produto aparece melhor.
        box_2d: [ymin, xmin, ymax, xmax] normalizado 0..1000 nesse quadro.
        confidence entre 0 e 1: use abaixo de 0,65 quando não tiver certeza do produto exato.
        Não invente produtos; se não houver nenhum, devolva a lista vazia.
    """.trimIndent()

    const val USER_SELECTED =
        "Estes quadros foram escolhidos pelo usuário porque mostram um produto que ele quer comprar: priorize o item em destaque."

    val JSON_FORMAT = """
        Responda SOMENTE com JSON no formato:
        {"spoken_summary": string|null, "screen_text": string|null,
         "items": [{"label": string, "brand": string|null, "search_query": string, "layer": "PRIMARY_FOCUS"|"SCENE_ITEM",
                    "detection_type": "EXACT_BRANDED_PRODUCT"|"VISUAL_FASHION_ITEM", "category": string|null,
                    "frame_index": number, "box_2d": [number], "confidence": number,
                    "evidence": ["AUDIO"|"VISION_OCR"|"SCREEN_TEXT"|"CAPTION"]}]}
    """.trimIndent()

    fun context(input: AnalysisInput) = buildString {
        if (input.userSelected) appendLine(USER_SELECTED)
        input.caption?.takeIf { it.isNotBlank() }?.let { appendLine("Legenda/título do vídeo: ${it.take(1500)}") }
        input.transcript?.takeIf { it.isNotBlank() }?.let { appendLine("Transcrição da fala do vídeo:\n${it.take(6000)}") }
    }
}
