package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.agent.base.VectorDb
import core.data.base.EmbeddingIndex
import core.network.OllamaClient
import jdk.internal.agent.Agent
import kotlinx.serialization.json.Json

class RagSearchTool(
    private val ollama: OllamaClient,
    private val index: EmbeddingIndex,
) : Tool {
    override val name: String = "semantic_search"
    override val description: String =
        "Semantic vector search in project documentation. Returns list of relevant text chunks with sources."
    override val parameters: ToolParameters = ToolParameters(
        schema = mapOf(
            "query" to "string: natural language query",
            "top_k" to "int: number of chunks to retrieve (default 5)"
        )
    )

    override suspend fun execute(ctx: ToolContext): ToolResult {
        val query = ctx.args["query"] as? String
            ?: return ToolResult.Error("Missing required parameter 'query'")
        val topK = (ctx.args["top_k"] as? Number)?.toInt() ?: 5

        val withRag = ollama.ragAnswerWithSources(question = query, index, askModel = "qwen2.5:3b", topK = topK)

        return ToolResult.Ok(withRag.answer)
    }
}
