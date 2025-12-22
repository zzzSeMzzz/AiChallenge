package core.network

import core.data.olama.OllamaEmbeddingRequest
import core.data.olama.OllamaEmbeddingResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OllamaEmbeddingClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "mxbai-embed-large"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun embed(texts: List<String>): List<List<Float>> {
        return texts.map { embedSingle(it) }
    }

    suspend fun embedSingle(text: String): List<Float> {
        val response: OllamaEmbeddingResponse = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbeddingRequest(model, text))
        }.body()
        return response.embedding
    }
}