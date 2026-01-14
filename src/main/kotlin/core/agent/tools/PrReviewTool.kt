package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.data.base.EmbeddingIndex
import core.network.OllamaClient

class PrReviewTool(
    private val githubPrTool: GithubPrTool,
    private val index: EmbeddingIndex,
    private val ollama: OllamaClient
) : Tool {
    override val name = "pr_review"
    override val description = "AI-powered code review for GitHub PR"
    override val parameters = ToolParameters(
        schema = mapOf(
            "owner" to "string",
            "repo" to "string",
            "pull_number" to "int"
        )
    )

    override suspend fun execute(ctx: ToolContext): ToolResult {
        val owner = ctx.args["owner"] as String
        val repo = ctx.args["repo"] as String
        val prNumber = (ctx.args["pull_number"] as Number).toInt()

        try {
            // 1. Получаем контекст PR
            val prCtx = githubPrTool.execute(ctx)
            val prText = (prCtx as? ToolResult.Ok)?.content ?: ""

            // 2. RAG: похожие места в коде/доке
            val ragQuery = "Code review context for: ${prText.take(1000)}"
           /* val ragChunks = vectorDb.semanticSearch(ragQuery, topK = 8)
            val ragContext = ragChunks.joinToString("\n\n") { chunk ->
                "File: ${chunk.source}\n${chunk.text}"
            }*/

            val answer = ollama.ragAnswerWithSources(question = ragQuery, index, askModel = "qwen2.5:3b", topK = 8)

            // 3. Промпт для ревью
            val prompt = """
            Ты — опытный разработчик и код-ревьюер.
            Сделай детальное ревью PR в формате Markdown.

            === PR КОНТЕКСТ ===
            $prText

            === ПРОЕКТНЫЙ КОНТЕКСТ (похожий код/правила) ===
            ${answer.answer}

            РЕВЬЮ должно содержать:
            ## Общее впечатление
            ## Плюсы ✅
            ## Проблемы ⚠️ (файл:строка)
            ## Рекомендации
            ## Итог: approve/changes requested
            """.trimIndent()

            // 4. Генерация ревью
            val review = ollama.ask(prompt, "qwen2.5:3b")
            return ToolResult.Ok(review)

        } catch (e: Exception) {
            return ToolResult.Error("Review failed: ${e.message}")
        }
    }
}
