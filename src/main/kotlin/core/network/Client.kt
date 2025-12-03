package core.network

import core.BuildConfig
import core.SERVER_URL
import core.data.ChatMessage
import core.data.YaGptRequest
import core.data.YaGptResponse
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
        query: String,
        systemPromptString: String? = null,
        formatAsJson: Boolean = false
    ): String {
        val systemPrompt = systemPromptString?.let {
            ChatMessage.system(it)
        }

        return try {
            val request = YaGptRequest.create(
                text = query,
                systemPrompt = systemPrompt,
                jsonObject = formatAsJson
            )

            val response = post(request)

            response.result.alternatives.firstOrNull()?.message?.text
                ?: "Нет ответа от модели."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }


    suspend fun askYaGpt(
        messages: List<ChatMessage>, // ← теперь принимаем список сообщений
    ): String {
        return try {
            val request = YaGptRequest.createWithMessages(
                messages = messages,
            )

            val response = post(request)

            response.result.alternatives.firstOrNull()?.message?.text
                ?: "Нет ответа от модели."
        } catch (e: Exception) {
            "Error: ${e.message}"
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
        client.close()
    }
}