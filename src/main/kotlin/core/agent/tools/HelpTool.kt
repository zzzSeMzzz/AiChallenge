package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DevHelpPayload(
    val question: String,
    val docs: String,  // JSON строка с массивом чанков
    val git: String
)

class HelpTool(
    private val ragTool: RagSearchTool,
    private val gitTool: Tool?, // MCP-инструмент для git (опционально)
) : Tool {
    override val name: String = "dev_help"
    override val description: String =
        "Answer questions about the current project using documentation (RAG) and git context."
    override val parameters: ToolParameters = ToolParameters(
        schema = mapOf(
            "question" to "string: developer question about this project"
        )
    )

    override suspend fun execute(ctx: ToolContext): ToolResult {
        val question = ctx.args["question"] as? String
            ?: return ToolResult.Error("Missing 'question' parameter")

        // 1) RAG по документации
        val ragResult = ragTool.execute(
            ctx.copy(args = mapOf("query" to question, "top_k" to 6))
        )
        val ragJson = (ragResult as? ToolResult.Ok)?.content ?: "[]"

        // 2) опциональный контекст git
        val gitInfo = gitTool?.let {
            val gitRes = it.execute(
                ctx.copy(args = mapOf("command" to "status+branch"))
            )
            (gitRes as? ToolResult.Ok)?.content ?: ""
        } ?: ""

        val payload = DevHelpPayload(
            question = question,
            docs = ragJson,     // уже готовый JSON массив
            git = gitInfo
        )

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        // 3) Собираем единый JSON, который потом скармливается LLM
        val combinedJson = json.encodeToString(payload)
        return ToolResult.Ok(combinedJson)
    }
}
