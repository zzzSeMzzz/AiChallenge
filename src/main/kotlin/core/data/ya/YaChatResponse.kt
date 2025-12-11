package core.data.ya

import core.utils.AiAnswer
import kotlinx.serialization.Serializable

@Serializable
data class YaGptResponse(
    val result: Result
) : AiAnswer {
    override fun answer() = result.alternatives.firstOrNull()?.message?.text
    ?: "Нет ответа от модели."

    override fun totalTokens() = result.usage?.totalTokens?.toIntOrNull() ?: 0

    override fun totalPrice() = result.usage?.calcCost() ?: 0.0

    override fun completionTokens() = result.usage?.completionTokens?.toIntOrNull() ?: 0

    override fun promptTokens() = result.usage?.inputTextTokens?.toIntOrNull() ?: 0
}

@Serializable
data class Result(
    val alternatives: List<Alternative>,
    val usage: Usage?
)

@Serializable
data class Alternative(
    val message: YaMessage,
    val status: String?
)

@Serializable
data class Usage(
    //входящие и исходящие токены 0,20 ₽	0,20 ₽
    val inputTextTokens: String?,
    val completionTokens: String?,
    val totalTokens: String?
) {
    fun calcCost(): Double {
        return (inputTextTokens?.toIntOrNull() ?: 0) * 0.2+ (completionTokens?.toIntOrNull() ?: 0) * 0.2
    }
}