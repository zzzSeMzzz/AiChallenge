package core.agent.base

import core.data.base.EmbeddedChunk

interface VectorDb {
    suspend fun semanticSearch(query: String, topK: Int = 5): List<EmbeddedChunk>
}