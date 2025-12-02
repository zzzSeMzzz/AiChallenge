package core.data

import core.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val text: String
)

@Serializable
data class YaGptRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<ChatMessage>
) {
    companion object {
        fun create(text: String, steaming: Boolean = false): YaGptRequest {
           return YaGptRequest(
               "gpt://${BuildConfig.CLOUD_FOLDER}/yandexgpt-lite",
               CompletionOptions(steaming, 0.3, "150"),
               listOf(ChatMessage("user", text))
           )
        }
    }
}
@Serializable
data class CompletionOptions(
    val stream: Boolean,
    val temperature: Double,
    val maxTokens: String
)