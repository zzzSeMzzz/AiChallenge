package core.agent.tools

import core.BuildConfig
import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.utils.McpClientManager
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class GithubPrTool : Tool {

    val mcpClient = McpClientManager.createGitHub(BuildConfig.GITHUB_TOKEN)
    val transport = McpClientManager.transports[McpClientManager.GITHUB_CLIENT]

    override val name = "github_pr"

    override val description = "через MCP получает diff и файлы из PR"

    override val parameters = ToolParameters(
        schema = mapOf(
            "owner" to "string: repo owner",
            "repo" to "string: repo name",
            "pr_number" to "int: PR number"
        )
    )

    override suspend fun execute(ctx: ToolContext): ToolResult {
        mcpClient.connect(transport!!)

        val owner = ctx.args["owner"] as String
        val repo = ctx.args["repo"] as String
        val prNumber = (ctx.args["pull_number"] as Number).toInt()

        try {
            // 1. PR мета
            val prMeta = mcpClient.callTool("get_pull_request", mapOf(
                "owner" to owner,
                "repo" to repo,
                "pull_number" to prNumber
            ))

            // 2. Файлы PR
            val prFiles = mcpClient.callTool("get_pull_request_files", mapOf(
                "owner" to owner,
                "repo" to repo,
                "pull_number" to prNumber
            ))

            // 3. Diff (если есть метод, иначе из файлов)
            val prDiff = mcpClient.callTool("get_pr_diff", mapOf(
                "owner" to owner,
                "repo" to repo,
                "pull_number" to prNumber
            )).content.firstOrNull()?.let { (it as TextContent).text } ?: ""

            val context = buildString {
                append("PR #$prNumber\n")
                append(prMeta.content.firstOrNull()?.let { (it as TextContent).text } ?: "")
                append("\n\nFiles changed:\n")
                append(prFiles.content.firstOrNull()?.let { (it as TextContent).text } ?: "")
                if (prDiff.isNotBlank()) {
                    append("\n\nDiff preview:\n")
                    append(prDiff.take(2000))
                }
            }

            return ToolResult.Ok(context.trim())
        } catch (e: Exception) {
            return ToolResult.Error("GitHub MCP error: ${e.message}")
        }
    }
}
