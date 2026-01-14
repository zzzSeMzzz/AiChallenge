package core.agent.base

interface Tool {
    val name: String
    val description: String
    val parameters: ToolParameters
    suspend fun execute(ctx: ToolContext): ToolResult
}