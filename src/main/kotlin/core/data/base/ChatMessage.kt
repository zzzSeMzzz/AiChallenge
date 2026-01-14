package core.data.base

import core.data.perplexety.PerMessage
import core.data.ya.YaMessage
import core.utils.AiClientType
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    @Transient
    val toolCalls: List<ToolCall>? = null,
    @Transient
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

    override fun toString(): String {
        return when(role) {
            Role.USER -> "User: $content"
            Role.ASSISTANT -> "Assistant: $content"
            Role.SYSTEM -> "System: $content"
            Role.TOOL -> "Tool: $content"
        }
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