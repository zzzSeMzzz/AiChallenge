package core.network

import core.data.base.EmbeddingIndex
import core.data.olama.OllamaEmbeddingRequest
import core.data.olama.OllamaEmbeddingResponse
import core.data.olama.OllamaErrorResponse
import core.data.olama.OllamaGenerateRequest
import core.data.olama.OllamaGenerateResponse
import core.utils.rag.TextPreprocessor
import core.utils.rag.buildRagPrompt
import core.utils.rag.retrieveTopK
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OllamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434",
    private val defaultModel: String = "mxbai-embed-large"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000 // Максимальное время запроса — 60 сек
            connectTimeoutMillis = 30_000  // Таймаут подключения
            socketTimeoutMillis = 60_000   // Чтение/запись
        }

        /*install(Logging) {
            level = LogLevel.ALL
        }*/
    }

    suspend fun ask(prompt: String, model: String = defaultModel): String {
        val resp: OllamaGenerateResponse = client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(OllamaGenerateRequest(model, prompt, stream = false))
        }.body()
        return resp.response.trim()
    }

    suspend fun embed(texts: List<String>, model: String = defaultModel): List<List<Float>> {
        return texts.map { embedSingle(it) }
    }


    suspend fun embedSingle(text: String, model: String = defaultModel): List<Float> {
        val cleanText = TextPreprocessor.cleanText(text)

        // ✅ Диагностика проблемных символов
        /*if (cleanText.contains('\r')) {
            println("⚠️  НАЙДЕН \\r в тексте: ${cleanText.take(100)}")
            error("Текст содержит \\r после очистки!")
        }

        if (cleanText.contains(Regex("\\n{3,}"))) {
            println("⚠️  Слишком много \\n: ${cleanText.take(100)}")
        }

        println("🔄 Эмбеддинг [${cleanText.length} символов, ${cleanText.lines().size} строк]:")
        println("   📝 '${cleanText.take(80)}...'")*/

        //println("embedSingle $cleanText")

        val response: OllamaEmbeddingResponse? = try {
            // Мы вызываем post, но не просим сразу конвертировать в body()
            val httpResponse = client.post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(OllamaEmbeddingRequest(defaultModel, cleanText))
            }

            // Проверяем статус ответа
            if (httpResponse.status.value in 200..299) {
                // Если всё хорошо, десериализуем в успешный объект
                httpResponse.body<OllamaEmbeddingResponse>()
            } else {
                // Если статус плохой (400, 404, 500), читаем текст ошибки из JSON
                val errorText = httpResponse.bodyAsText()
                val errorDetail = try {
                    // Пытаемся распарсить JSON ошибки
                    Json.decodeFromString<OllamaErrorResponse>(errorText).error
                } catch (e: Exception) {
                    // Если там не JSON, а просто текст
                    errorText
                }

                // Выводим конкретный текст ошибки, который вернул сервер
                println("Сервер Ollama вернул ошибку: $errorDetail")
                null
            }
        } catch (e: io.ktor.client.plugins.ResponseException) {
            // Обработка ошибок через плагин (если включен ExpectSuccess)
            val errorBody = e.response.bodyAsText()
            println("Ошибка выполнения запроса (ResponseException): $errorBody")
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            // Вот здесь вы оказывались раньше!
            // Это происходит, когда структура JSON не совпала с OllamaEmbeddingResponse
            println("Ошибка десериализации: возможно, сервер прислал JSON ошибки вместо данных.")
            println("Технические детали: ${e.localizedMessage}")
            null
        } catch (e: Exception) {
            // Самый общий случай (проблемы с интернетом, таймауты)
            println("Критическая сетевая ошибка: ${e.message}")
            null
        }



        return response?.embedding ?: emptyList()
    }


    suspend fun answerWithRag(
        question: String,
        index: EmbeddingIndex,
        askModel: String,
        topK: Int = 5
    ): String {
        val relevant = retrieveTopK(question, index, this, topK)
        val prompt = buildRagPrompt(question, relevant)
        return ask(prompt, askModel)
    }
}