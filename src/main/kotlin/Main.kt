import core.data.ChatMessage
import core.network.Client

suspend fun main(args: Array<String>) {
    println("Консольный чат с YandexGpt для создания ТЗ мобильного приложения")
    println("Введите exit для выхода")
    println("Модель будет задавать вопросы, чтобы понять, что нужно создать.")

    val mobilePromt = """
                Ты — эксперт по разработке мобильных приложений.
                Твоя задача — помочь пользователю составить техническое задание (ТЗ) для мобильного приложения.
                Задавай пользователю по одному уточняющему вопросу, чтобы понять:
                - Цель приложения
                - Целевую аудиторию
                - Основные функции
                - Платформу (iOS, Android, обе)
                - Дизайн и UX-предпочтения
                - Интеграции (API, соцсети, оплаты и т.д.)
                - Бюджет и сроки (если известно)
                
                После того как вся необходимая информация собрана, скажи: "Информация собрана. Генерирую ТЗ..." и выдай полное, структурированное ТЗ.
                Не спеши. Задавай вопросы по одному, пока не будешь готов к финальному выводу.
            """.trimIndent()

    val messages = mutableListOf<ChatMessage>().apply {
        add(ChatMessage.system(mobilePromt))
    }

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        if (input.lowercase() in listOf("exit", "выход", "quit")) {
            println("Чат завершён.")
            Client.close()
            return
        }

        // Добавляем сообщение пользователя
        messages.add(ChatMessage.user(input))

        // Отправляем всю историю
        val answer = Client.askYaGpt(messages)
        println("YaGpt: $answer")

        // Добавляем ответ модели
        messages.add(ChatMessage.assistant(answer))
    }
}