package core.data.ya

import core.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class YaMessage(
    val role: String,
    val text: String
) {
    companion object {
        fun system(text: String) = YaMessage("system", text)

        fun assistant(text: String) = YaMessage("assistant", text)

        fun user(text: String) = YaMessage("user", text)
    }
}

@Serializable
data class YaGptRequest(
    val modelUri: String,
    val jsonObject: Boolean?,
    val completionOptions: CompletionOptions,
    val messages: List<YaMessage>,
) {
    companion object {
        fun create(
            text: String,
            systemPrompt: YaMessage? = null,
            jsonObject: Boolean? = null,
            steaming: Boolean = false,
        ): YaGptRequest {
           val messages = mutableListOf<YaMessage>()
           messages.add(YaMessage("user", text))
           systemPrompt?.let { messages.add(it) }
           return YaGptRequest(
               modelUri = "gpt://${BuildConfig.CLOUD_FOLDER}/yandexgpt",//-lite
               completionOptions = CompletionOptions(steaming, 0.4, "256"),
               messages = messages,
               jsonObject = jsonObject
           )
        }

        fun createWithMessages(
            messages: List<YaMessage>,
            steaming: Boolean = false,
            temperature: Double,  // Низкая для детерминизма [web:22]
            model: String,
            maxTokens: String = "1024"
        ): YaGptRequest {

            return YaGptRequest(
                modelUri = "gpt://${BuildConfig.CLOUD_FOLDER}/$model",
                completionOptions = CompletionOptions(steaming, temperature, maxTokens),
                messages = messages,
                jsonObject = null
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