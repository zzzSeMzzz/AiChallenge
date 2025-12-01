import core.data.YaGptRequest
import core.network.RetrofitModule
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val service = RetrofitModule.service

    println("Консольный чат с YandexGpt")
    println("Введите exit для выхода")
    while (true) {
        print("Вы: ")
        val input = readlnOrNull()?.trim() ?: continue

        when (input.lowercase()) {
            "exit", "выход", "quit" -> {
                println("Чат завершён.")
                return@runBlocking // Завершаем блок runBlocking, что приводит к завершению main
            }
            else -> {
                try {
                    val request = YaGptRequest.create(input)
                    val response = service.send(request)
                    val answer = response.result.alternatives.firstOrNull()?.message?.text
                        ?: "Нет ответа от модели."
                    println("YaGpt: $answer")
                } catch (e: Exception) {
                    println("Ошибка при обращении к API: ${e.message}")
                }
            }
        }
    }
}