

import core.data.base.ChatMessage
import core.data.base.LlmClient
import core.network.OllamaClient
import core.utils.*
import core.utils.rag.buildIndexFromDirectory
import core.utils.rag.loadIndex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.logging.Logger


suspend fun main() = runBlocking {
    val logger = Logger.getLogger("McpAgent")
    val clientType = AiClientType.YANDEX_GPT
    //val model = "sonar"
    val model = "yandexgpt-lite"
    val maxTokens = 1000

    // ✅ Дефолтный системный промпт
    val defaultSystemPrompt = """
    """.trimIndent()

    //buildIndexFromDirectory("src/main/res/readme/", "nomic-embed-text:latest")
    val ollama = OllamaClient(defaultModel = "nomic-embed-text:latest")

    val index = loadIndex(Json { ignoreUnknownKeys = true },"index.json")

    /*val q = "Расскажи как на котлин c помощью библиотек создать mcp сервер"


    val withRag = ollama.answerWithRag(question = q, index, askModel = "llama3.2", topK = 5)
    println("\n📚 С RAG:\n$withRag")*/

    var systemPrompt: String? = null//defaultSystemPrompt

    println()
    println("Консольный чат с $clientType, модель $model, maxTokens $maxTokens")
    println("Системный промпт по умолчанию установлен:")
    println(" > ${defaultSystemPrompt.lines().first()}...")
    println("Введите exit для выхода,")
    println("s: для замены системного промпта")
    println("rag: для запроса с RAG")


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

    /*val mcpClient = McpClientManager.createSavingClient()
    try {
        mcpClient.connect(transports[SAVE_TO_FILE_CLIENT]!!)
        val tools = mcpClient.listTools().tools

        println("✅ MCP-инструменты: ${tools.map { it.name }}")
    } catch (e: Exception) {
        logger.log(Level.WARNING, "Не удалось подключиться к MCP-серверу", e)
        println("⚠️ MCP-сервер недоступен. Будет работать без инструментов.")
    }*/

   /* val apkPath = "D:/asemchenko/projects/flutter/GRC/build/app/outputs/flutter-apk/app-debug.apk"  // свой путь

    val result = mcpClient.callTool(
        "deploy_android_app",
        mapOf(
            "apk_path" to apkPath,
            "package_name" to "getrentacar.app",
            "activity_name" to ".MainActivity"
        )
    )
    val out = result.content.joinToString("\n") { (it as TextContent).text }
    println("📱 deploy_android_app output:\n$out")*/

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
            input.startsWith("rag:") -> {
                val ragPrompt = input.substring(4).trim()
                println("Запрос с RAG: модель llama3.2")
                val withRag = ollama.answerWithRag(question = ragPrompt, index, askModel = "llama3.2", topK = 5)
                println("Agent: $withRag")
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

               /* val prefix = "RUN ANDROID_APP:"
                if (response.contains(prefix, ignoreCase = true)) {
                    println("🛠 LLM запросила запуск Android-приложения через MCP")

                    // Вытаскиваем часть после префикса
                    val tail = response.substringAfter(prefix, missingDelimiterValue = "").trim()
                    // Ожидаем формат: apk_path; package_name; activity_name
                    val parts = tail.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size < 3) {
                        val err = "Не удалось разобрать параметры RUN ANDROID_APP. Ожидаю: <apk_path>; <package_name>; <activity_name>"
                        println("❌ $err")
                        chatMemory.addAssistantMessage(err)
                        println("---")
                        continue
                    }

                    val apkPath = parts[0]
                    val packageName = parts[1]
                    val activityName = parts[2]

                    println("APK: $apkPath")
                    println("Package: $packageName")
                    println("Activity: $activityName")

                    try {
                        val result = mcpClient.callTool(
                            "deploy_android_app",
                            mapOf(
                                "apk_path" to apkPath,
                                "package_name" to packageName,
                                "activity_name" to activityName
                            )
                        )
                        val output = result.content.joinToString("\n") { (it as TextContent).text }
                        println("📱 deploy_android_app output:\n$output")

                        val finalText = """
                            Установил и запустил приложение:
                            APK: $apkPath
                            Пакет: $packageName
                            Активити: $activityName

                            Лог выполнения:
                            $output
                        """.trimIndent()

                        println("Agent: $finalText")
                        chatMemory.addAssistantMessage(finalText)
                    } catch (e: Exception) {
                        val err = "Не удалось установить/запустить Android-приложение: ${e.message}"
                        println("❌ $err")
                        logger.log(Level.SEVERE, "Ошибка deploy_android_app", e)
                        chatMemory.addAssistantMessage(err)
                    }

                    println("---")
                    continue
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
                }*/

                println("Agent: $response")
                println("Промпт токенов: ${answer?.promptTokens()}, completion: ${answer?.completionTokens()}, всего: ${answer?.totalTokens()}")
                chatMemory.addAssistantMessage(response)

                println("---")
            }
        }
    }
}

