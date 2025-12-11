
import core.data.base.ChatMessage
import core.data.base.LlmClient
import core.utils.AiAnswer
import core.utils.AiClientType
import core.utils.ClientManager
import core.utils.CompressedChatMemory


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
    val clientType = AiClientType.YANDEX_GPT
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
        summaryEveryN = 7
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
    }
}


