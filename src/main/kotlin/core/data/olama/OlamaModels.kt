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
    val prompt: String,
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


data class RAGConfig(
    val initialK: Int = 12,     // первый ретрив
    val minScore: Double = 0.70, // порог косинуса
    val finalK: Int = 4,        // финальный контекст для LLM
    val useRerank: Boolean = true,
    val askModel: String = "llama3.2"
)

data class RagAnswer(
    val answer: String,
    val sources: List<String>,// пути файлов
    val hadContext: Boolean
)