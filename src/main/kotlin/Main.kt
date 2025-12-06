
import core.data.perplexety.PerMessage
import core.network.Client
import core.network.PerClient

suspend fun main(args: Array<String>) {
    println("Консольный чат с Perplexity")
    println("Введите exit для выхода, s: для задания системного промптa")
    println("Первый system prompt: Ты шеф вовар известного ресторана")

    val messages = mutableListOf<PerMessage>().apply {
        add(PerMessage.system("Ты шеф вовар известного ресторана"))
    }

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        when {
            input.lowercase() in listOf("exit", "выход", "quit") -> {
                println("Чат завершён.")
                Client.close()
                return
            }
            input.startsWith("s:") -> {
                messages.add(PerMessage.system(input.substring(2).trim()))
                continue
            }
            else -> {
                messages.add(PerMessage.user(input))
            }
        }

        val answer = PerClient.askPerplexity(messages)
        println("Agent: $answer")

        // Добавляем ответ модели
        messages.add(PerMessage.assistant(answer))
    }
}