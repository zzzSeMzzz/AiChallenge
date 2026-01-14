package core.data.perplexety

import core.utils.AiAnswer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PerMessage(
    val role: String,
    val content: String
) {
    companion object {
        fun system(text: String) = PerMessage("system", text)

        fun assistant(text: String) = PerMessage("assistant", text)

        fun user(text: String) = PerMessage("user", text)
    }
}

@Serializable
data class ResponseFormat(
    val type: String,
    @SerialName("json_schema")
    val jsonSchema: JsonSchema
)

@Serializable
data class JsonSchema(
    val schema: JsonObject
)

@Serializable
data class PerplexityRequest(
    val model: String,
    val messages: List<PerMessage>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("temperature")
    val temperature: Double? = null,
)

@Serializable
data class Choice(
    val message: PerMessage,
    val index: Int? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class PerplexityCost(
    @SerialName("input_tokens_cost")
    val inputTokensCost: Double,
    @SerialName("output_tokens_cost")
    val outputTokensCost: Double,
    @SerialName("request_cost")
    val requestCost: Double,
    @SerialName("total_cost")
    val totalCost: Double,
)

@Serializable
data class PerplexityUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int,
    @SerialName("cost")
    val cost: PerplexityCost?,
)

@Serializable
data class PerplexityResponse(
    val choices: List<Choice>,
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val usage: PerplexityUsage? = null,
) : AiAnswer {
    override fun answer() = choices.firstOrNull()?.message?.content ?: "Нет ответа от модели."

    override fun totalTokens() = usage?.totalTokens ?: 0

    override fun totalPrice() = usage?.cost?.totalCost ?: 0.0

    override fun completionTokens() = usage?.completionTokens ?: 0

    override fun promptTokens() = usage?.totalTokens ?: 0
}