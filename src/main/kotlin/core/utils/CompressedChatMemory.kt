package core.utils

import core.data.base.ChatMessage
import core.data.base.LlmClient
import core.data.base.Role

class CompressedChatMemory(
    private val llmClient: LlmClient,
    private val summaryEveryN: Int = 10
) {
    private val summaries = mutableListOf<String>()
    private val recentMessages = mutableListOf<ChatMessage>()

    fun addUserMessage(text: String) {
        recentMessages.add(ChatMessage(Role.USER, text))
    }

    fun addAssistantMessage(text: String) {
        recentMessages.add(ChatMessage(Role.ASSISTANT, text))
    }

    suspend fun buildContext(systemPrompt: String?): List<ChatMessage> {
        maybeSummarizeIfNeeded()

        val context = mutableListOf<ChatMessage>()

        // Системный промпт
        systemPrompt?.let {
            context.add(ChatMessage(Role.SYSTEM, it))
        }

        // Последний summary как системный контекст
        val summary = summaries.lastOrNull()
        summary?.let {
            context.add(ChatMessage(Role.SYSTEM, "Предыдущий контекст диалога: $it"))
        }

        // Последние 15 сырых сообщений
        context.addAll(recentMessages.takeLast(15))

        return context
    }

    // Публичные геттеры для статистики
    val summaryCount: Int get() = summaries.size
    val recentCount: Int get() = recentMessages.size
    fun getLastSummary(): String? = summaries.lastOrNull()

    private suspend fun maybeSummarizeIfNeeded() {
        if (recentMessages.size < summaryEveryN) return

        val historyText = recentMessages.joinToString("\n") { "${it.role}: ${it.content}" }
        val summaryPrompt = listOf(
            ChatMessage(Role.SYSTEM, """
                Создай КРАТКОЕ резюме диалога (2-4 предложения, max 100 слов).
                Сохрани: цели пользователя, ключевые факты, открытые задачи, имена/даты.
                Только факты в связной форме, без "Пользователь спросил...".
            """.trimIndent()),
            ChatMessage(Role.USER, "Диалог:\n$historyText")
        )

        val summaryAnswer = llmClient.chat(summaryPrompt)
        val summary = summaryAnswer?.answer() ?: "Не удалось суммировать"
        summaries.add(summary)
        recentMessages.clear()
    }
}
