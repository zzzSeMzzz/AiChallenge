package core.agent.base

class ToolExecutor(
    private val registry: ToolRegistry,
    private val interceptors: List<ToolInterceptor> = emptyList()
) {
    suspend fun execute(call: ToolCall, session: SessionContext): ToolResult {
        val tool = registry.find(call.name)
            ?: return ToolResult.Error("Unknown tool: ${call.name}")

        val baseContext = ToolContext(call.arguments, session)

        // Тип цепочки: suspend (ToolContext) -> ToolResult
        val terminal: suspend (ToolContext) -> ToolResult = { ctx ->
            tool.execute(ctx)
        }

        val chain: suspend (ToolContext) -> ToolResult =
            interceptors.foldRight(terminal) { interceptor, next ->
                { ctx -> interceptor.intercept(ctx, next) }
            }

        return chain(baseContext)
    }
}