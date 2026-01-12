package core.agent.base

interface ToolInterceptor {
    suspend fun intercept(ctx: ToolContext, next: suspend (ToolContext) -> ToolResult): ToolResult
}