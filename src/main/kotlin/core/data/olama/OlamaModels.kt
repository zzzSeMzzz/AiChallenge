package core.data.olama
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String  // у Ollama это prompt, не input
)

@Serializable
data class OllamaEmbeddingResponse(
    val embedding: List<Float>
)