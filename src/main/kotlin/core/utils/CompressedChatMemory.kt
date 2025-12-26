package core.utils

import core.data.base.*
import kotlinx.serialization.json.Json
import java.io.File

class CompressedChatMemory(
    private val llmClient: LlmClient,
    private val summaryEveryN: Int = 10,
    private val summaryFile: File = File("chat_summaries.json"),
    private val historyFile: File = File("chat_history.json"),
) {
    private val summaries = mutableListOf<SerializableSummary>()
    private val recentMessages = mutableListOf<ChatMessage>()
    private var nextSummaryId: Long = 1L
    private val fullHistory = mutableListOf<ChatMessage>()

    init {
        loadFromFilesIfExists()
    }

    val fullHistoryCount: Int get() = fullHistory.size
    fun getFullHistory(): List<ChatMessage> = fullHistory.toList()
    fun getLastNMessages(n: Int): List<ChatMessage> = fullHistory.takeLast(n)

    fun addUserMessage(text: String) {
        val message = ChatMessage(Role.USER, text)
        recentMessages += message
        fullHistory += message  // ✅ СОХРАНЯЕМ В ПОЛНУЮ ИСТОРИЮ
        saveHistoryToFile()
    }

    fun addAssistantMessage(text: String) {
        val message = ChatMessage(Role.ASSISTANT, text)
        recentMessages += message
        fullHistory += message
        saveHistoryToFile()
    }

    fun addAssistantMessage(content: String, toolCalls: List<ToolCall>? = null) {
        val message = ChatMessage(Role.ASSISTANT, content, toolCalls = toolCalls)
        recentMessages.add(message)
        fullHistory += message
        saveHistoryToFile()
    }

    fun addToolMessage(toolCallId: String, content: String) {
        val message = ChatMessage(Role.TOOL, content, toolCallId = toolCallId)
        recentMessages.add(message)
        fullHistory += message
        saveHistoryToFile()
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

        saveToFiles()
    }

    private fun saveHistoryToFile() {
        try {
            val historyJson = Json.encodeToString(fullHistory)
            historyFile.writeText(historyJson)
        } catch (_: Exception) {
            // silent fail
        }
    }

    fun printStats() {
        println("📊 СТАТИСТИКА ЧАТА:")
        println("  Всего сообщений: $fullHistoryCount")
        println("  Summaries: $summaryCount")
        println("  Recent: $recentCount")
        println("  Последние 3 сообщения:")
        getLastNMessages(3).forEachIndexed { i, msg ->
            println("    ${msg.role}: ${msg.content.take(100)}...")
        }
    }

    fun printMessages() {
        fullHistory.forEach { println(it) }
    }

    private fun saveToFiles() {
        saveToSummaryFile()
        saveHistoryToFile()
    }

    private fun saveToSummaryFile() {
        try {
            val state = SerializableMemoryState(
                summaries = summaries.toList(),
                lastSummaryId = nextSummaryId
            )
            val json = Json.encodeToString(state)
            summaryFile.writeText(json)
        } catch (_: Exception) {
            // silent fail
        }
    }

    private fun loadFromFilesIfExists() {
        loadSummaryFromFileIfExists()
        loadHistoryFromFileIfExists()
    }

    private fun loadSummaryFromFileIfExists() {
        if (!summaryFile.exists()) return
        try {
            val json = summaryFile.readText()
            val state = Json.decodeFromString<SerializableMemoryState>(json)
            summaries.clear()
            summaries += state.summaries
            nextSummaryId = state.lastSummaryId
        } catch (_: Exception) {
            println("⚠️  Не удалось загрузить summaries")
        }
    }

    private fun loadHistoryFromFileIfExists() {
        if (!historyFile.exists()) return
        try {
            val json = historyFile.readText()
            val loadedHistory = Json.decodeFromString<List<ChatMessage>>(json)
            fullHistory.clear()
            fullHistory += loadedHistory
            println("✅ Загружено ${fullHistory.size} сообщений из истории")
        } catch (_: Exception) {
            println("⚠️  Не удалось загрузить полную историю")
        }
    }
}
