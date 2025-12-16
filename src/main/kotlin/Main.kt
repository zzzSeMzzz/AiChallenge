

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) = runBlocking {
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

    println("Старт клиента")

    val process = ProcessBuilder(
        "java",
        "-jar",
        "D:/projects/java/AiChallenge/server/build/libs/MPCServer-1.0-SNAPSHOT.jar"
    )
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    println("Серверный процесс запущен, pid=${process.pid()}")

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

// 💡 КЛЮЧЕВОЕ ИЗМЕНЕНИЕ: Создаем Job для отслеживания жизненного цикла сессии
    val sessionJob = Job()

    try {
        client.connect(transport)

        // Регистрируем колбэк, который завершит наш Job, когда сервер закроет сессию
        // (хотя клиентский SDK может не иметь прямого onClose колбэка для Client/Transport,
        // клиент обычно управляет закрытием сам, поэтому мы используем finally)

        val toolsResult = client.listTools()
        val tools = toolsResult.tools

        println("Доступные MCP-инструменты:")
        tools.forEach { tool ->
            println("- ${tool.name}: ${tool.description}")
        }

        // --- Здесь ваша программа завершает свою работу, что приводит к ошибке ---
        // Если вы хотите, чтобы она работала дольше, вам нужно интерактивное взаимодействие
        // (как в закомментированном чате в вашем первом примере)

    } finally {
        // Гарантированное закрытие клиента, которое ДОЛЖНО инициировать
        // отправку сигнала onClose серверу.
        println("Отправляем сигнал закрытия клиенту...")
        client.close()

        // 💡 ВАЖНО: Ждем, пока процесс сервера завершится САМ,
        // получив сигнал onClose от нашего client.close().
        // Не вызываем process.destroy() сразу!

        if (process.isAlive) {
            println("Ожидаем чистого завершения серверного процесса...")
            // process.waitFor() блокирует поток, что подходит в runBlocking
            process.waitFor()
            println("Серверный процесс завершился.")
        }

        // process.destroy() теперь можно вызвать просто как подстраховку,
        // но он уже должен быть не нужен.
        // process.destroy()
    }
}


