package core.network

import core.BuildConfig
import core.SERVER_URL
import core.data.ya.YaMessage
import core.data.ya.YaGptRequest
import core.data.ya.YaGptResponse
import core.utils.AiAnswer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


object Client {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        /*install(Logging) {
            //logger = Logger. // ← используем SLF4J как бэкенд
            level = io.ktor.client.plugins.logging.LogLevel.ALL
        }*/
    }


    suspend fun askYaGpt(
        messages: List<YaMessage>, // ← теперь принимаем список сообщений
        temperature: Double = 0.1,
        model: String = "yandexgpt-lite",
        maxTokens: Int = 512
    ): AiAnswer {
        return try {
            val request = YaGptRequest.createWithMessages(
                messages = messages,
                temperature =  temperature,
                model = model,
                maxTokens = maxTokens.toString()
            )
            val response = post(request)
            //response.result.alternatives.firstOrNull()?.message?.text ?: "Нет ответа от модели."
            response
        } catch (e: Exception) {
            object : AiAnswer {
                override fun answer()= "Error: ${e.message}"

                override fun totalTokens() = 0

                override fun totalPrice() = 0.0

                override fun completionTokens() = 0

                override fun promptTokens() = 0
            }
        }
    }

    private suspend fun post(request: YaGptRequest): YaGptResponse {
        val response: YaGptResponse =
            client.post("$SERVER_URL/foundationModels/v1/completion") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Api-Key ${BuildConfig.YA_API_KEY}")
                setBody(request)
            }.body()
        return response
    }

    fun close() {
        try {
            client.close()
        } catch (e: Exception) { }
    }
}