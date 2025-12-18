

import core.data.base.ChatMessage
import core.data.base.FunctionCall
import core.data.base.LlmClient
import core.data.base.ToolCall
import core.utils.*
import core.utils.McpClientManager.SAVE_TO_FILE_CLIENT
import core.utils.McpClientManager.WEB_SEARCH_CLIENT
import core.utils.McpClientManager.transports
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


suspend fun main() = runBlocking {
    val logger = Logger.getLogger("McpAgent")
    val clientType = AiClientType.YANDEX_GPT
    //val model = "sonar"
    val model = "yandexgpt-lite"
    val maxTokens = 1000

    // ✅ Дефолтный системный промпт
    val defaultSystemPrompt = """
        Ты — ассистент, у которого НЕТ прямого доступа к инструментам, но твой хост-приложение умеет по твоим подсказкам вызывать MCP-инструменты:

        - search_web(query, limit) — ищет информацию в интернете.
        - save_to_file(path, content) — сохраняет текст в файл.

        Когда пользователь просит:
        - «найди в интернете», «поискать информацию», «сделай конспект/summary по теме», «собери материалы и сохрани» и т.п.,
        ты НЕ даёшь готовый ответ, а выписываешь одну строку в формате:

        SEARCH_AND_SAVE: <краткое описание темы для поиска>

        Примеры:
        - Вопрос: «Сделай конспект по Kotlin coroutines и сохрани.»
          Ты должен ответить: SEARCH_AND_SAVE: конспект по Kotlin coroutines`
        - Вопрос: «Поискать лучшие практики по MCP в Kotlin и сохранить результат.»
          Ты должен ответить: SEARCH_AND_SAVE: лучшие практики MCP в Kotlin`

        После строки SEARCH_AND_SAVE: ...` НИЧЕГО больше не добавляй.

        Если пользователь просит обычный ответ, без поиска и сохранения, отвечай обычно, без `SEARCH_AND_SAVE`.

    """.trimIndent()

    var systemPrompt: String? = defaultSystemPrompt

    println("Консольный чат с $clientType, модель $model, maxTokens $maxTokens")
    println("Системный промпт по умолчанию установлен:")
    println(" > ${defaultSystemPrompt.lines().first()}...")
    println("Введите exit для выхода,")
    println("s: для замены системного промпта")


    val llmClient = object : LlmClient {
        override suspend fun chat(messages: List<ChatMessage>): AiAnswer? {
            return ClientManager.ask(
                client = clientType,
                messages = messages,
                model = model,
                temperature = 0.3,
                maxTokens = maxTokens,
            )
        }
    }

    val chatMemory = CompressedChatMemory(
        llmClient,
        summaryEveryN = 10
    )


    val mcpClient = McpClientManager.createSavingClient()
    try {
        mcpClient.connect(transports[SAVE_TO_FILE_CLIENT]!!)
        val tools = mcpClient.listTools().tools

        println("✅ MCP-инструменты: ${tools.map { it.name }}")
    } catch (e: Exception) {
        logger.log(Level.WARNING, "Не удалось подключиться к MCP-серверу", e)
        println("⚠️ MCP-сервер недоступен. Будет работать без инструментов.")
    }


    val webSearchClient = McpClientManager.createWebSearchClient()
    try {
        webSearchClient.connect(transports[WEB_SEARCH_CLIENT]!!)
        val tools = webSearchClient.listTools().tools
        println("✅[web-search-mcp] MCP-инструменты: ${tools.map { it.name }}")
    } catch (e: Exception) {
        logger.log(Level.WARNING, "Не удалось подключиться к MCP-серверу", e)
        println("⚠️ MCP-сервер недоступен. Будет работать без инструментов.")
    }

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: break

        when {
            input.lowercase() in listOf("exit", "выход", "quit") -> {
                println("Чат завершён.")
                println("Создано summaries: ${chatMemory.summaryCount}")
                ClientManager.close()
                McpClientManager.closeClients()
                return@runBlocking
            }
            input.startsWith("s:") -> {
                systemPrompt = input.substring(2).trim()
                println("Системный промпт установлен")
                continue
            }
            input == "st:" -> {
                println("=== СТАТИСТИКА СЖАТИЯ ===")
                println("Summaries: ${chatMemory.summaryCount}")
                println("Текущая история: ${chatMemory.recentCount} сообщений")
                chatMemory.getLastSummary()?.let {
                    println("Последний summary: $it")
                }
                println("---")
                continue
            }
            else -> {
                chatMemory.addUserMessage(input)

                // ✅ Строим сжатый контекст
                val context = chatMemory.buildContext(systemPrompt)
                var answer = llmClient.chat(context)

                var response = answer?.answer() ?: "Не удалось получить ответ."

                val prefix = "SEARCH_AND_SAVE:"
                if (response.contains(prefix, ignoreCase = true)) {
                    println("🛠 LLM запросила пайплайн search_web → summarize → save_to_file")
                    // 1.1. Тема для поиска
                    val topic = response.removePrefix(prefix).trim()
                    println("Тема поиска: $topic")

                    val searchArgs = mapOf(
                        "query" to topic,
                        "limit" to 3
                    )
                    val searchResult = webSearchClient.callTool("search_web", searchArgs)
                    val searchText = searchResult.content.joinToString("\n") { (it as TextContent).text }
                    println("🔎 Результаты поиска (обрезано):")
                    println(searchText.lines().take(5).joinToString("\n"))
                    println("----")

                    val summaryPrompt = """
                    Составь краткий связный конспект по теме: "$topic"
                    Используй текст ниже как сырой материал:
                    $searchText
                    """.trimIndent()

                    val summaryContext = listOf(
                        ChatMessage.user(summaryPrompt)
                    )
                    val summaryAnswer = llmClient.chat(summaryContext)
                    val summary = summaryAnswer?.answer() ?: "Не удалось построить summary."
                    println("📄 Summary:\n$summary\n----")


                    val result = mcpClient.callTool(
                        "save_to_file",
                        mapOf(
                            "path" to "summaries/summary-${System.currentTimeMillis()}.txt",
                            "content" to summary
                        )
                    )

                    val output = result.content.joinToString("\n") { (it as TextContent).text }
                    println("🌤 MCP: save to file: $output")


                } else if (response.contains("get_forecast", ignoreCase = true)) {
                    println("🛠 LLM запросила вызов get_forecast → вызываем MCP")

                    val (lat, lon) = when {
                        input.contains("москва", ignoreCase = true) -> 55.7558 to 37.6176
                        input.contains("лондон", ignoreCase = true) -> 51.5074 to -0.1278
                        input.contains("париж", ignoreCase = true) -> 48.8566 to 2.3522
                        else -> 55.7558 to 37.6176
                    }

                    val args = mapOf("latitude" to lat, "longitude" to lon)

                    try {
                        val result = mcpClient.callTool("get_forecast", args)
                        val output = result.content.joinToString("\n") { (it as TextContent).text }
                        println("🌤 MCP: $output")

                        // Добавляем вызов инструмента
                        val toolCall = ToolCall(
                            id = "call-weather-${UUID.randomUUID()}",
                            type = "function",
                            function = FunctionCall("get_forecast", args)
                        )

                        chatMemory.addAssistantMessage("", toolCalls = listOf(toolCall))
                        chatMemory.addToolMessage(toolCall.id, output)

                        // Второй запрос к LLM с результатом
                        val followUpContext = chatMemory.buildContext(systemPrompt)
                        val finalAnswer = llmClient.chat(followUpContext)
                        response = finalAnswer?.answer() ?: response

                        println("Agent: $response")
                        println("Промпт токенов: ${finalAnswer?.promptTokens()}, completion: ${finalAnswer?.completionTokens()}, всего: ${finalAnswer?.totalTokens()}")

                        // Сохраняем финальный ответ
                        chatMemory.addAssistantMessage(response)
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "Ошибка вызова MCP", e)
                        println("❌ Ошибка вызова MCP: ${e.message}")
                        chatMemory.addAssistantMessage("Извините, не удалось получить данные о погоде.")
                    }
                }
                else {
                    println("Agent: $response")
                    println("Промпт токенов: ${answer?.promptTokens()}, completion: ${answer?.completionTokens()}, всего: ${answer?.totalTokens()}")
                    chatMemory.addAssistantMessage(response)
                }

                println("---")
            }
        }
    }
}

