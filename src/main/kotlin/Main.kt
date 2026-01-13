

import core.agent.base.*
import core.agent.tools.*
import core.data.base.ChatMessage
import core.data.base.LlmClient
import core.network.OllamaClient
import core.utils.*
import core.utils.rag.loadIndex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.logging.Logger


/**
 * Реализуйте простой чат-бот, который:
 * - хранит историю диалога,
 * - при каждом новом вопросе ищет контекст в базе документов (через ваш RAG),
 * - возвращает ответ с учётом найденной информации.
 *
 * Добавьте обязательный вывод: «источники», откуда был взят ответ.
 *
 * Результат: Мини-чат (CLI/веб) с RAG-памятью и ссылками на источники
 */

suspend fun main() = runBlocking {
    val logger = Logger.getLogger("McpAgent")
    val clientType = AiClientType.YANDEX_GPT
    //val model = "sonar"
    val model = "yandexgpt-lite"
    val maxTokens = 700

    // ✅ Дефолтный системный промпт
    val defaultSystemPrompt = """
    """.trimIndent()


   /* val gitMcpClient = McpClientManager.createGitHub(BuildConfig.GITHUB_TOKEN)
    val transport = McpClientManager.transports[McpClientManager.GITHUB_CLIENT]
    gitMcpClient.connect(transport!!)

    //gitMcpClient.listTools().tools.forEach { tool -> println(tool.name) }

    val result = gitMcpClient.callTool(
        "get_pull_request",  // название tool в MCP сервере
        mapOf(
            "owner" to "zzzSeMzzz",
            "repo" to "AiChallenge",
            "pull_number" to 1
        )
    )

    println("PR diff:")
    result.content.forEach { content ->
        println((content as TextContent).text)
    }**/

    //buildIndexFromDirectory("src/main/res/project_descr/", "nomic-embed-text:latest")
    val ollama = OllamaClient(defaultModel = "nomic-embed-text:latest")

    val index = loadIndex(Json { ignoreUnknownKeys = true },"index.json")

    val registry = DefaultToolRegistry()
    //val vectorDb = JsonVectorDb(index, ollama)
    val ragTool = RagSearchTool(ollama, index)

    // MCP git tool (реальный — с вызовом MCP-сервера)
    val gitTool: Tool? = null//GitStatusTool(/* mcpClient */)

    val helpTool = HelpTool(ragTool, gitTool)

    registry.register(ragTool)
    registry.register(helpTool)
    val githubPrTool = GithubPrTool()
    registry.register(githubPrTool)
    registry.register(PrReviewTool(githubPrTool, index, ollama))
    // плюс регистрируешь MCP-инструменты, HTTP_API и т.п.
    val executor = ToolExecutor(registry)


    var systemPrompt: String? = null//defaultSystemPrompt

    println()
    println("Консольный чат с $clientType, модель $model, maxTokens $maxTokens")
    //println("Системный промпт по умолчанию установлен:")
    //println(" > ${defaultSystemPrompt.lines().first()}...")
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

    //chatMemory.printMessages()

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
            input.startsWith("/help") -> {
                val question = input.removePrefix("/help").trim()
                if (question.isBlank()) {
                    println("Формат: /help <вопрос о проекте>")
                    continue
                }

                val session = SessionContext(userId = "local", chatId = "cli")
                val call = ToolCall(
                    id = "dev_help",
                    name = "dev_help",
                    arguments = mapOf("question" to question)
                )

                val toolResult = executor.execute(call, session)

                val payload: DevHelpPayload? = (toolResult as? ToolResult.Ok)?.content?.let {
                    try {
                        Json.decodeFromString<DevHelpPayload>(it)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (payload == null) {
                    println("DevHelper: не удалось обработать ответ инструмента.")
                    continue
                }

                // Дальше либо отдаёшь payload в LLM, либо сам красиво рендеришь
                println("DevHelper: вопрос: ${payload.question}")
                println("Git контекст:\n${payload.git}")
                println("Agent: ${payload.docs.answer}\nИсточники: ${payload.docs.sources.joinToString(", ")}")
            }
            input.startsWith("s:") -> {
                systemPrompt = input.substring(2).trim()
                println("Системный промпт установлен")
                continue
            }
            input.startsWith("rag:") -> {
                val question = input.substring(4).trim()
                val localOllamaModel = "qwen2.5:3b"
                println("Запрос с RAG: модель $localOllamaModel")
                val withRag = ollama.ragAnswerWithSources(question = question, index, askModel = localOllamaModel, topK = 5)
                println("Agent: ${withRag.answer}\nИсточники: ${withRag.sources.joinToString(", ")}")


                chatMemory.addUserMessage(question)
                chatMemory.addAssistantMessage(
                    buildString {
                        append(withRag.answer)
                        append("\n\nИсточники:\n")
                        withRag.sources.joinToString(", ")
                    }.trim()
                )

                if(!withRag.hadContext) {
                    println("Запрос к $clientType")
                    chatMemory.addUserMessage(input)

                    // ✅ Строим сжатый контекст
                    val context = chatMemory.buildContext(systemPrompt)
                    val answer = llmClient.chat(context)

                    val response = answer?.answer() ?: "Не удалось получить ответ."

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
                    //println("Промпт токенов: ${answer?.promptTokens()}, completion: ${answer?.completionTokens()}, всего: ${answer?.totalTokens()}")
                    chatMemory.addAssistantMessage(response)
                }

               /* println("\n1️⃣ БАЗОВЫЙ RAG (top-8 без фильтра):")
                val basic = ollama.answerWithAdvancedRAG(
                    question,
                    index,
                    RAGConfig(initialK = 8, minScore = 0.0, useRerank = false, askModel = localOllamaModel)
                )
                println(basic)

                // УЛУЧШЕННЫЙ RAG (фильтр + rerank)
                println("\n2️⃣ УЛУЧШЕННЫЙ RAG (фильтр 0.7 + LLM-rerank):")
                val advanced = ollama.answerWithAdvancedRAG(
                    question, index,
                    RAGConfig(initialK = 12, minScore = 0.70, useRerank = true, askModel = localOllamaModel)
                )
                println(advanced)*/

                continue
            }
            input == "st:" -> {
                println("=== СТАТИСТИКА СЖАТИЯ ===")
                chatMemory.printStats()  // ✅ НОВАЯ СТАТИСТИКА
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
                //println("Промпт токенов: ${answer?.promptTokens()}, completion: ${answer?.completionTokens()}, всего: ${answer?.totalTokens()}")
                chatMemory.addAssistantMessage(response)

                println("---")
            }
        }
    }
}

