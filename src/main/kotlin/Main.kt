import core.network.Client
import kotlinx.serialization.json.jsonObject

suspend fun main(args: Array<String>) {
    println("Консольный чат с YandexGpt")
    println("Введите exit для выхода")

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
                val answer = Client.askYaGpt(input, true)//.replace("\n", "").replace("`", "")
                println("YaGpt: $answer")


                try {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val jsonObject = json.parseToJsonElement(answer).jsonObject
                    println("✅ JSON распознан:")
                    println(jsonObject)
                } catch (e: Exception) {
                    println("❌ Не удалось распарсить как JSON. Возможно, модель не подчинилась.")
                }
            }
        }
    }
}