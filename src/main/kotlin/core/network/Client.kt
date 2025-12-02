package core.network

import core.BuildConfig
import core.SERVER_URL
import core.data.YaGptRequest
import core.data.YaGptResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
    }


    suspend fun askYaGpt(query: String): String {
        return try {
            val request = YaGptRequest.create(query)

            val response: YaGptResponse = client.post("$SERVER_URL/foundationModels/v1/completion") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Key ${BuildConfig.YA_API_KEY}")
                setBody(request)
            }.body()

            response.result.alternatives.firstOrNull()?.message?.text
                ?: "Нет ответа от модели."
        } catch (e: Exception) {
            "Error: ${e.message}"
        } finally {
            client.close()
        }
    }
}