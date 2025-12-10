
import core.utils.AiClientType
import core.utils.ClientManager


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
    val clientType = AiClientType.YANDEX_GPT
    //val model = "sonar"
    val model = "yandexgpt-lite"
    val maxTokens = 1000

    println("Консольный чат с $clientType, модель $model, maxTokens $maxTokens")
    println("Введите exit для выхода,\ns: для задания системного промптa")


    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue
        when {
            input.lowercase() in listOf("exit", "выход", "quit") -> {
                println("Чат завершён.")
                ClientManager.close()
                return
            }
            input.startsWith("s:") -> {
                ClientManager.ask(
                    client = clientType,
                    input = input.substring(2).trim(),
                    model = model,
                    temperature = 0.3,
                    isSystemPrompt = true,
                    maxTokens = maxTokens,
                )
                continue
            }
            else -> {
                val tStart = System.nanoTime()
                val answer = ClientManager.ask(
                    client = clientType,
                    input = input.substring(2).trim(),
                    model = model,
                    temperature = 0.3,
                    isSystemPrompt = false,
                    maxTokens = maxTokens,
                )

                println("Agent: ${answer?.answer()}")

                println("Промпт токенов: ${answer?.promptTokens()}, completion токенов ${answer?.completionTokens()}")
            }
        }
    }
}


