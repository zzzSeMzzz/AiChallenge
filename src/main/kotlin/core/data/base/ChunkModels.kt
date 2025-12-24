package core.data.base

import kotlinx.serialization.Serializable

data class Chunk(val id: String, val source: String, val text: String)


@Serializable
data class EmbeddedChunk(
    val id: String,
    val source: String,
    val text: String,
    val embedding: List<Float>,
    val tokens: Int
)

@Serializable
data class EmbeddingIndex(
    val dimension: Int,
    val chunks: List<EmbeddedChunk>,
    val totalChunks: Int
)


@Serializable
data class ScoredChunk(
    val chunk: EmbeddedChunk,
    val score: Double  // косинусная похожесть [0.0..1.0]
)