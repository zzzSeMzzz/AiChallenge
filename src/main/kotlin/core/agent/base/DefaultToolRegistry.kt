package core.agent.base

class DefaultToolRegistry : ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    override fun register(tool: Tool) { tools[tool.name] = tool }
    override fun find(name: String) = tools[name]
    override fun all() = tools.values.toList()
}