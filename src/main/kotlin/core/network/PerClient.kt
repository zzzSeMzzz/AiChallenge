package core.network

import core.BuildConfig
import core.SERVER_URL
import core.SERVER_URL_PERPLEXITY
import core.data.perplexety.Message
import core.data.perplexety.PerplexityRequest
import core.data.perplexety.PerplexityResponse
import core.data.ya.ChatMessage
import core.data.ya.YaGptRequest
import core.data.ya.YaGptResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
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
        /*install(Logging) {
            //logger = Logger. // ← используем SLF4J как бэкенд
            level = io.ktor.client.plugins.logging.LogLevel.ALL
        }*/
    }


    suspend fun askPerplexity(
        messages: MutableList<Message> // ← теперь принимаем список сообщений
    ): String {
        return try {

            val response = post(messages)
            return response.choices.firstOrNull()?.message?.content ?: "Нет ответа от модели."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun post(messages: MutableList<Message>): PerplexityResponse {
        val request = PerplexityRequest(
            model = "sonar-pro",
            messages = messages
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
        } catch (e: Exception) { }
    }
}