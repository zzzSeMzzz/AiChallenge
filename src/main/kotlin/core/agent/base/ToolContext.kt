package core.agent.base

data class ToolContext(
    val args: Map<String, Any?>,
    val session: SessionContext
)