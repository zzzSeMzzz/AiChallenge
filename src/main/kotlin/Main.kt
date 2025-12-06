
import core.data.perplexety.PerMessage
import core.network.Client

suspend fun main(args: Array<String>) {
    println("Консольный чат с Perplexity")
    println("Введите exit для выхода, s: для задания системного промптa")

    val messages = mutableListOf<PerMessage>()


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
            }
            else -> {
                messages.add(PerMessage.user(input))
            }
        }
    }
}