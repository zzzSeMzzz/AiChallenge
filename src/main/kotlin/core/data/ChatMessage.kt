package core.data

import core.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val text: String
) {
    companion object {
        fun system(text: String) = ChatMessage("system", text);
    }
}

@Serializable
data class YaGptRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<ChatMessage>,
) {
    companion object {
        fun create(
            text: String,
            systemPrompt: ChatMessage? = null,
            steaming: Boolean = false,
        ): YaGptRequest {
           val messages = mutableListOf<ChatMessage>()
           messages.add(ChatMessage("user", text))
           systemPrompt?.let { messages.add(it) }
           return YaGptRequest(
               modelUri = "gpt://${BuildConfig.CLOUD_FOLDER}/yandexgpt-lite",
               completionOptions = CompletionOptions(steaming, 0.3, "256"),
               messages = messages,
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