package core.agent.base

enum class ToolType {
    MCP,
    // Claude-style provider
    RAG,
    // Vector DB
    HTTP_API,
    // внешние сервисы
    SYSTEM
    // meta: logging, debug
}