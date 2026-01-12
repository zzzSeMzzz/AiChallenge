package core.agent.base

sealed class ToolResult {
    data class Ok(val content: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}