
import core.network.PerClient
import core.utils.AiClientType
import core.utils.ClientManager


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
    val clientType = AiClientType.PERPLEXITY
    val model = "sonar"//sonar Reasoning

    println("Консольный чат с $clientType")
    println("Введите exit для выхода,\ns: для задания системного промптa")


    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        when {
            input.lowercase() in listOf("exit", "выход", "quit") -> {
                println("Чат завершён.")
                PerClient.close()
                return
            }
            input.startsWith("s:") -> {
                ClientManager.ask(
                    client = clientType,
                    input = input.substring(2).trim(),
                    model = model,
                    temperature = 0.3,
                    isSystemPrompt = true
                )
                continue
            }
            else -> {
                val answer = ClientManager.ask(
                    client = clientType,
                    input = input.substring(2).trim(),
                    model = model,
                    temperature = 0.3,
                    isSystemPrompt = false
                )

                println("Agent: ${answer?.answer()}")
            }
        }
    }
}


