

import core.data.base.ChatMessage
import core.data.base.FunctionCall
import core.data.base.LlmClient
import core.data.base.ToolCall
import core.utils.AiAnswer
import core.utils.AiClientType
import core.utils.ClientManager
import core.utils.CompressedChatMemory
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

suspend fun main() = runBlocking {
    val logger = Logger.getLogger("McpAgent")
    val clientType = AiClientType.PERPLEXITY
    val model = "sonar"
    // val model = "yandexgpt-lite"
    val maxTokens = 1000

    // ✅ Дефолтный системный промпт
    val defaultSystemPrompt = """
        Ты — ассистент, который помогает с погодой.
        Если пользователь спрашивает о погоде в городе — НЕ ОТВЕЧАЙ САМ.
        Вместо этого, вызови инструмент: get_forecast(latitude=..., longitude=...).
        Используй реальные координаты:
          - Москва: latitude=55.7558, longitude=37.6176
          - Лондон: latitude=51.5074, longitude=-0.1278
          - Париж: latitude=48.8566, longitude=2.3522
        Пример вызова: get_forecast(latitude=55.7558, longitude=37.6176)
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

    // 🛠 MCP: Запуск сервера
    val process = ProcessBuilder(
        "java", "-jar", "D:/projects/java/AiChallenge/server/build/libs/MPCServer-1.0-SNAPSHOT.jar"
    ).redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
    )

    val mcpClient = Client(Implementation("kotlin-mcp-client", "1.0.0"))

    try {
        mcpClient.connect(transport)
        val tools = mcpClient.listTools().tools
        println("✅ MCP-инструменты: ${tools.map { it.name }}")
    } catch (e: Exception) {
        logger.log(Level.WARNING, "Не удалось подключиться к MCP-серверу", e)
        println("⚠️ MCP-сервер недоступен. Будет работать без инструментов.")
    }

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        when {
            input.lowercase() in listOf("exit", "выход", "quit") -> {
                println("Чат завершён.")
                println("Создано summaries: ${chatMemory.summaryCount}")
                ClientManager.close()
                mcpClient.close()
                process.destroyForcibly().waitFor()
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

                // 🔍 Проверяем, нужно ли вызвать get_forecast
                if (response.contains("get_forecast", ignoreCase = true)) {
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
                } else {
                    println("Agent: $response")
                    println("Промпт токенов: ${answer?.promptTokens()}, completion: ${answer?.completionTokens()}, всего: ${answer?.totalTokens()}")
                    chatMemory.addAssistantMessage(response)
                }

                println("---")
            }
        }
    }
}

