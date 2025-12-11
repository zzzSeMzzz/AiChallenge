package core.data.base

import core.data.perplexety.PerMessage
import core.data.ya.YaMessage
import core.utils.AiClientType

data class ChatMessage(
    val role: Role,
    val content: String,
) {
    fun toClientMessage(clientType: AiClientType): Any {
        return when (clientType) {
            AiClientType.YANDEX_GPT -> YaMessage(role.name.lowercase(), content)
            AiClientType.PERPLEXITY -> PerMessage(role.name.lowercase(), content)
        }
    }

    fun toPerplexity(): PerMessage {
        return PerMessage(role.name.lowercase(), content)
    }

    fun toYa(): YaMessage {
        return YaMessage(role.name.lowercase(), content)
    }
}