package core.agent.base

interface ToolRegistry {
    fun register(tool: Tool)
    fun find(name: String): Tool?
    fun all(): List<Tool>
}