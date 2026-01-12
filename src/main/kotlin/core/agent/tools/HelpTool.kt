package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.data.olama.RagAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DevHelpPayload(
    val question: String,
    val docs: RagAnswer,  // JSON строка с массивом чанков
    val git: String
)

class HelpTool(
    private val ragTool: RagSearchTool,
    private val gitTool: Tool? = null
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

        // 1) RAG по документации — НОВЫЙ КОНТЕКСТ
        val ragCtx = ToolContext(
            args = mapOf("query" to question, "top_k" to 6),
            session = ctx.session
        )
        val ragResult = ragTool.execute(ragCtx)
        //println("ragResult: $ragResult")
        val ragJson = (ragResult as? ToolResult.Ok)?.content ?: ""

        // 2) опциональный контекст git
        val gitInfo = gitTool?.let {
            val gitCtx = ToolContext(
                args = mapOf("command" to "status+branch"),
                session = ctx.session
            )
            val gitRes = it.execute(gitCtx)
            (gitRes as? ToolResult.Ok)?.content ?: ""
        } ?: ""

        // 3) Собираем payload
        val payload = DevHelpPayload(
            question = question,
            docs = Json.decodeFromString(ragJson),
            git = gitInfo
        )

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        val combinedJson = json.encodeToString(payload)
        return ToolResult.Ok(combinedJson)
    }
}
