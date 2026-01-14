package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.data.base.EmbeddingIndex
import core.network.OllamaClient

class SupportRagTool(
    private val index: EmbeddingIndex,
    private val ollama: OllamaClient,
) : Tool {
    override val name = "support_semantic_search"
    override val description = "Semantic search in support docs and FAQ"
    override val parameters = ToolParameters(
        schema = mapOf(
            "query" to "string: user question",
            "top_k" to "int: docs to retrieve (default 5)"
        )
    )

    override suspend fun execute(ctx: ToolContext): ToolResult {
        val query = ctx.args["query"] as? String
            ?: return ToolResult.Error("Missing query")
        val topK = (ctx.args["top_k"] as? Number)?.toInt() ?: 5

        /*val chunks = db.semanticSearch(query, topK)
        val result = chunks.joinToString("\n\n---\n\n") { c ->
            "Источник: ${c.source}\n${c.text}"
        }*/

        val withRag = ollama.ragAnswerWithSources(question = query, index, askModel = "qwen2.5:3b", topK = topK)


        return ToolResult.Ok(withRag.answer)
    }
}
