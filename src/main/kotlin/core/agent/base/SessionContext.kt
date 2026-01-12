package core.agent.base

data class SessionContext(
    val userId: String?,
    val chatId: String?,
    val extra: Map<String, Any?> = emptyMap()
)