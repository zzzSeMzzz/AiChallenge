package core.data.olama
import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String,
)

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,  // у Ollama это prompt, не input
    val stream: Boolean = false
)

@Serializable  // ✅ Правильная структура для Ollama
data class OllamaEmbeddingResponse(
    val embedding: List<Float>
)

@Serializable
data class OllamaErrorResponse(
    val error: String
)

@Serializable
data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean
)