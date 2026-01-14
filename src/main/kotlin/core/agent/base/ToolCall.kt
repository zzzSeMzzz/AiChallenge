package core.agent.base

data class ToolCall(
    val id: String,          // Уникальный ID вызова (генерируется LLM)
    val name: String,        // Имя вызываемого инструмента (например, "get_weather")
    val arguments: Map<String, Any?>    // Аргументы в формате JSON-строки
)