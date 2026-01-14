package core.agent.base

import core.data.base.EmbeddedChunk
import core.data.base.EmbeddingIndex
import core.network.OllamaClient
import core.utils.rag.cosineSim

class JsonVectorDb(
    private val index: EmbeddingIndex,
    private val embedder: OllamaClient
) : VectorDb {

    override suspend fun semanticSearch(query: String, topK: Int): List<EmbeddedChunk> {
        val qEmbedding = embedder.embedSingle(query)
        return index.chunks
            .map { it to cosineSim(qEmbedding, it.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }
}
