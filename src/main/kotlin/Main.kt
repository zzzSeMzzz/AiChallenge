import core.data.perplexety.PerMessage
import core.network.PerClient

suspend fun main(args: Array<String>) {
    println("Консольный чат с Perplexity")
    println("Введите exit для выхода,\ns: для задания системного промптa")

    val messages = mutableListOf<PerMessage>()/*.apply {
        add(PerMessage.system("Ты сценарист"))
    }*/

    val models = listOf(
        "sonar-mini",
        "sonar-small",
        "sonar-pro"
    )

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
                //remove old system prompt
                if(messages.firstOrNull()?.role == "system") {
                    messages.removeAt(0)
                }
                messages.add(0, PerMessage.system(input.substring(2).trim()))
                continue
            }
            else -> {
                messages.add(PerMessage.user(input))
            }
        }

        /*val answer = Client.askYaGpt(
            messages,
            temperature = 1.2
        )*/
        val answer = PerClient.askPerplexity(
            messages,
            temperature = 1.2
        )
        println("Agent: $answer")

        // Добавляем ответ модели
        messages.add(PerMessage.assistant(answer))
    }
}