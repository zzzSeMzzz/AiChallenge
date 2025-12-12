package core.utils

import core.data.base.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CompressedChatMemory(
    private val llmClient: LlmClient,
    private val summaryEveryN: Int = 10,
    private val summaryFile: File = File("chat_summaries.json"),
) {
    private val summaries = mutableListOf<SerializableSummary>()
    private val recentMessages = mutableListOf<ChatMessage>()
    private var nextSummaryId: Long = 1L

    init {
        loadFromFileIfExists()
    }

    fun addUserMessage(text: String) {
        recentMessages += ChatMessage(Role.USER, text)
    }

    fun addAssistantMessage(text: String) {
        recentMessages += ChatMessage(Role.ASSISTANT,text)
    }

    suspend fun buildContext(systemPrompt: String?): List<ChatMessage> {
        maybeSummarizeIfNeeded()

        val context = mutableListOf<ChatMessage>()

        systemPrompt?.let {
            context += ChatMessage(Role.SYSTEM, it)
        }

        val lastSummary = summaries.lastOrNull()
        if (lastSummary != null) {
            context += ChatMessage(
                Role.SYSTEM,
                "Краткое резюме предыдущего диалога: ${lastSummary.text}"
            )
        }

        context += recentMessages.takeLast(15)

        return context
    }

    // Публичные геттеры для статистики
    val summaryCount: Int get() = summaries.size
    val recentCount: Int get() = recentMessages.size
    fun getLastSummary(): String? = summaries.lastOrNull()?.text

    private suspend fun maybeSummarizeIfNeeded() {
        if (recentMessages.size < summaryEveryN) return

        val historyText = recentMessages.joinToString("\n") { "${it.role}: ${it.content}" }

        val summaryPrompt = listOf(
            ChatMessage(
                Role.SYSTEM,
                """
                Создай КРАТКОЕ резюме диалога (2–4 предложения, до 100 слов).
                Сохрани цели пользователя, важные факты и незавершённые задачи.
                Пиши связным текстом, без "Пользователь спросил...".
                """.trimIndent()
            ),
            ChatMessage(Role.USER, "Диалог:\n$historyText")
        )

        val answer = llmClient.chat(summaryPrompt)
        val summaryText = answer?.answer() ?: return

        val summary = SerializableSummary(id = nextSummaryId++, text = summaryText)
        summaries += summary
        recentMessages.clear()

        saveToFileSafe()
    }

    private fun saveToFileSafe() {
        try {
            val state = SerializableMemoryState(
                summaries = summaries.toList(),
                lastSummaryId = nextSummaryId
            )
            val json = Json.encodeToString(state) // -> String [web:27][web:28]
            summaryFile.writeText(json)
        } catch (_: Exception) {
            // можно залогировать
        }
    }

    private fun loadFromFileIfExists() {
        if (!summaryFile.exists()) return
        try {
            val json = summaryFile.readText()          // [web:33][web:36]
            val state = Json.decodeFromString<SerializableMemoryState>(json)
            summaries.clear()
            summaries += state.summaries
            nextSummaryId = state.lastSummaryId
        } catch (_: Exception) {
            // файл битый — игнорируем
        }
    }
}
