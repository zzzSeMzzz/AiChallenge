import core.network.Client
import kotlinx.serialization.json.jsonObject

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

    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        when (input.lowercase()) {
            "exit", "выход", "quit" -> {
                println("Чат завершён.")
                Client.close()
                return
            }
            else -> {
                val answer = Client.askYaGpt(
                    query = input,
                    systemPromptString = mobilePromt
                )
                println("YaGpt: $answer")

            }
        }
    }
}