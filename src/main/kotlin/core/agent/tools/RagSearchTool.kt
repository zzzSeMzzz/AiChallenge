package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.agent.base.VectorDb
import kotlinx.serialization.json.Json

class RagSearchTool(
    private val db: VectorDb
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

        val results = db.semanticSearch(query, topK)

        if (results.isEmpty()) {
            return ToolResult.Ok("[]") // пустой JSON-массив
        }

        // Возвращаем JSON, чтобы LLM мог парсить
        /*val json = buildString {
            append("[")
            results.forEachIndexed { i, chunk ->
                if (i > 0) append(",")
                append("{")
                append("\"id\":\"").append(chunk.id).append("\",")
                append("\"source\":\"").append(chunk.source).append("\",")
                append("\"text\":").append(JSONObject.quote(chunk.text))
                append("}")
            }
            append("]")
        }*/
       val json = Json.encodeToString(results)

        return ToolResult.Ok(json)
    }
}
