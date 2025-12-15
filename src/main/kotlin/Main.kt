

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport

import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
   /* val clientType = AiClientType.YANDEX_GPT
    //val model = "sonar"
    val model = "yandexgpt-lite"
    val maxTokens = 1000

    println("Консольный чат с $clientType, модель $model, maxTokens $maxTokens")
    println("Введите exit для выхода,\ns: для задания системного промптa")

    var systemPrompt: String? = null

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

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue
        when {
            input.lowercase() in listOf("exit", "выход", "quit") -> {
                println("Чат завершён.")
                println("Создано summaries: ${chatMemory.summaryCount}")
                ClientManager.close()
                return
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
                val answer = llmClient.chat(context)

                println("Agent: ${answer?.answer()}")
                println("Промпт токенов: ${answer?.promptTokens()}, completion: ${answer?.completionTokens()}, всего: ${answer?.totalTokens()}")

                // ✅ Сохраняем ответ в память
                chatMemory.addAssistantMessage(answer?.answer() ?: "")
                println("---")
            }
        }
    }*/

    val process = ProcessBuilder(
        "node",
        File("D:/asemchenko/lessons/ai/index.js").absolutePath  // поправь путь при необходимости
    )
        .directory(File("D:/asemchenko/lessons/ai"))
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    // 2. Транспорт поверх stdio процесса
    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),   // stdout сервера
        output = process.outputStream.asSink().buffered() // stdin сервера
    )

    // 3. Создаём MCP‑клиент
    val client = Client(
        clientInfo = Implementation(
            name = "kotlin-mcp-client",
            version = "0.1.0"
        )
    )

    // 4. Подключаемся
    client.connect(transport)

    // сгенерённый API‑класс для tools
    val toolsResult = client.listTools()
    val tools = toolsResult.tools

    println("Доступные MCP-инструменты:")
    tools.forEach { tool ->
        println("- ${tool.name}: ${tool.description}")
    }

    // 6. Закрываемся
    client.close()
    process.destroy()
}


