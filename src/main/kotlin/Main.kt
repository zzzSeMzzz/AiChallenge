import core.data.perplexety.PerMessage
import core.data.ya.ChatMessage
import core.network.Client
import core.network.PerClient

suspend fun main(args: Array<String>) {
    println("Консольный чат с Perplexity для создания ТЗ мобильного приложения")
    println("Введите exit для выхода")
    println("Модель будет задавать вопросы, чтобы понять, что нужно создать.")

    val mobilePromt = "Ты — эксперт по разработке мобильных приложений.Твоя задача составить ТЗ."
    val mobilePromt1 = "Спроси про платформу, целевую аудиторию, функционал, дизайн. когда соберешь все 4 ответа, выдай ТЗ."
    val mobilePromt2 = "Один вопрос один ответ"
    val mobilePromt3 = "Ровно один вопрос за раз и ничего лишнего.Нельзя предполагать ответы пользователя"
    val mobilePromt4 = "максимум 5-7 вопрос и после пришли готовое ТЗ"

    val messages = mutableListOf<PerMessage>().apply {
        add(PerMessage.system("$mobilePromt $mobilePromt1 $mobilePromt3"))
    }

    //messages.add(ChatMessage.user(mobilePromt4))

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        if (input.lowercase() in listOf("exit", "выход", "quit")) {
            println("Чат завершён.")
            Client.close()
            return
        }

        // Добавляем сообщение пользователя
        messages.add(PerMessage.user(input))

        // Отправляем всю историю
        val answer = PerClient.askPerplexity(messages)
        println("Agent: $answer")

        // Добавляем ответ модели
        messages.add(PerMessage.assistant(answer))
    }
}