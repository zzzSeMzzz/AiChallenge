
import core.data.ya.ChatMessage
import core.network.Client

suspend fun main(args: Array<String>) {
    println("Консольный чат с YaGPT")
    println("Введите exit для выхода, s: для задания системного промптa")
    println("Первый system prompt: Ты шеф вовар известного ресторана")

    val messages = mutableListOf<ChatMessage>().apply {
        //add(PerMessage.system("Ты шеф вовар известного ресторана"))
        add(ChatMessage.system("Ты шеф повар известного ресторана"))
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
                //remove old system prompt
                if(messages.firstOrNull()?.role == "system") {
                    messages.removeAt(0)
                }
                messages.add(0, ChatMessage.system(input.substring(2).trim()))
                continue
            }
            else -> {
                messages.add(ChatMessage.user(input))
            }
        }

        //val answer = PerClient.askPerplexity(messages)
        val answer = Client.askYaGpt(messages)
        println("Agent: $answer")

        // Добавляем ответ модели
        messages.add(ChatMessage.assistant(answer))
    }
}