package core.utils.rag

import core.data.base.EmbeddedChunk
import core.data.base.EmbeddingIndex
import core.data.base.ScoredChunk
import core.network.OllamaClient
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt

//val json = Json { ignoreUnknownKeys = true }

fun loadIndex(json: Json, path: String = "index.json"): EmbeddingIndex =
    json.decodeFromString(File(path).readText())

fun cosineSim(a: List<Float>, b: List<Float>): Double {
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        val x = a[i].toDouble()
        val y = b[i].toDouble()
        dot += x * y
        na += x * x
        nb += y * y
    }
    return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
}


//отсеиваем лишине
suspend fun retrieveTopK(
    query: String,
    index: EmbeddingIndex,
    embedder: OllamaClient,
    k: Int = 5
): List<EmbeddedChunk> {
    val qEmbedding = embedder.embedSingle(query)
    return index.chunks
        .map { it to cosineSim(qEmbedding, it.embedding) }
        .sortedByDescending { it.second }
        .take(k)
        .map { it.first }
}

suspend fun retrieveTopKScoredChunks(
    query: String,
    index: EmbeddingIndex,
    embedder: OllamaClient,
    k: Int = 5
): List<ScoredChunk> {
    val qEmbedding = embedder.embedSingle(query)
    return index.chunks
        .map { it to cosineSim(qEmbedding, it.embedding) }
        .sortedByDescending { it.second }
        .take(k)
        .map { ScoredChunk( it.first, it.second) }
}


// Фильтрация по порогу (быстро!)
fun filterByThreshold(
    scoredChunks: List<ScoredChunk>,
    minScore: Double = 0.75  // ✅ ПОРОГ ОТСЕЧЕНИЯ
): List<ScoredChunk> {
    val filtered = scoredChunks.filter { it.score >= minScore }
    println("🔍 Фильтр: ${scoredChunks.size} → ${filtered.size} (порог $minScore)")
    return filtered
}

fun buildRagPrompt(
    question: String,
    contexts: List<EmbeddedChunk>
): String {
    val contextBlock = contexts.joinToString("\n\n---\n\n") { chunk ->
        "Источник: ${chunk.source}\nТекст:\n${chunk.text}"
    }

    return """
        Ты — ассистент, отвечающий строго по предоставленному контексту.
        Если информации в контексте недостаточно, явно напиши, что ответа в документах нет.

        КОНТЕКСТ:
        $contextBlock

        ВОПРОС:
        $question

        Ответь по-русски, сжатым текстом.
    """.trimIndent()
}
