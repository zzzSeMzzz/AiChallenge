package core.network

import core.BuildConfig
import core.SERVER_URL_PERPLEXITY
import core.data.perplexety.PerMessage
import core.data.perplexety.PerplexityRequest
import core.data.perplexety.PerplexityResponse
import core.utils.AiAnswer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


object PerClient {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000 // Максимальное время запроса — 60 сек
            connectTimeoutMillis = 30_000  // Таймаут подключения
            socketTimeoutMillis = 60_000   // Чтение/запись
        }
        /*install(Logging) {
            //logger = Logger. // ← используем SLF4J как бэкенд
            level = io.ktor.client.plugins.logging.LogLevel.ALL
        }*/
    }


    suspend fun askPerplexity(
        messages: List<PerMessage>,
        temperature: Double = 0.4,
        model: String = "sonar",
        maxTokens: Int = 512
    ): AiAnswer {
        return try {
            val response = post(messages, temperature, model, maxTokens)
            //return response.choices.firstOrNull()?.message?.content ?: "Нет ответа от модели."
            response
        } catch (e: Exception) {
            object : AiAnswer {
                override fun answer()= "Error: ${e.message}"

                override fun totalTokens() = 0

                override fun completionTokens() = 0

                override fun promptTokens() = 0

                override fun totalPrice() = 0.0
            }
        }
    }

    private suspend fun post(
        messages: List<PerMessage>,
        temperature: Double,
        model: String,
        maxTokens: Int
    ): PerplexityResponse {
        val request = PerplexityRequest(
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            temperature = temperature
        )

        val response: PerplexityResponse =
            client.post("$SERVER_URL_PERPLEXITY/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${BuildConfig.PERPLEXITY_API_KEY}")
                setBody(request)
            }.body()
        return response
    }

    fun close() {
        try {
            client.close()
        } catch (ignored: Exception) { }
    }
}