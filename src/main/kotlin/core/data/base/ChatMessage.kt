package core.data.base

import core.data.perplexety.PerMessage
import core.data.ya.YaMessage
import core.utils.AiClientType

data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
) {

    companion object {
        fun user(content: String) = ChatMessage(Role.USER, content)
        fun assistant(content: String) = ChatMessage(Role.ASSISTANT, content)
        fun system(content: String) = ChatMessage(Role.SYSTEM, content)
        fun tool(content: String, toolCallId: String) =
            ChatMessage(Role.TOOL, content, toolCallId = toolCallId)
    }

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


data class ToolResult(
    val name: String,
    val output: String,
    val isError: Boolean = false
)


data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: Map<String, Any>
)