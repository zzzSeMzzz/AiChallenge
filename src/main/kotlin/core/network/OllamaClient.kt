package core.network

import core.data.base.EmbeddingIndex
import core.data.base.ScoredChunk
import core.data.olama.*
import core.utils.rag.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json

class OllamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434",
    private val defaultModel: String = "mxbai-embed-large"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {

        install(ContentNegotiation) {
            json(json)
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

    suspend fun ask(prompt: String, model: String): String {
        /*val resp: OllamaGenerateResponse = client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(OllamaGenerateRequest(model, prompt, stream = false))
        }.body()
        return resp.response.trim()*/
        val result = generateStream(OllamaGenerateRequest(model, prompt, stream = true))

//        println()
//        println()
//        println()
//        println("=======================")
        return result//result.joinToString { it }
    }

    suspend fun generateStream(request: OllamaGenerateRequest): String {
        // Установим stream = true на всякий случай
        val effectiveRequest = request.copy(stream = true)

        val httpResponse = client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(effectiveRequest)
        }

        val channel = httpResponse.bodyAsChannel()
        val reader = channel.toInputStream().bufferedReader()
        val responses = mutableListOf<OllamaGenerateResponse>()
        val sb = StringBuilder()

        reader.use { br ->
            var line: String?
            while (br.readLine().also { line = it } != null) {
                line?.trim()?.let { jsonLine ->
                    if (jsonLine.isNotEmpty()) {
                        try {
                            val resp = json.decodeFromString<OllamaGenerateResponse>(jsonLine)
                            responses.add(resp)
                            sb.append(resp.response)
                            //print(resp.response)
                        } catch (e: Exception) {
                            println("Parse error on line: $jsonLine")
                        }
                    }
                }
            }
        }

        return sb.toString()//responses
    }

    suspend fun embed(texts: List<String>, model: String = defaultModel): List<List<Float>> {
        return texts.map { embedSingle(it, model) }
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
                setBody(OllamaEmbeddingRequest(model, cleanText))
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
        //println("rag prompt: $prompt")
        return ask(prompt, askModel)
    }


    suspend fun ragAnswerWithSources(
        question: String,
        index: EmbeddingIndex,
        askModel: String,
        minScore: Double = 0.7,
        topK: Int = 5
    ): RagAnswer {
        val relevant = retrieveTopKWithScore(question, index, this, topK)
        if (relevant.isEmpty()) {
            return RagAnswer(
                answer = "В локальных документах нет подходящего контекста.",
                sources = emptyList(),
                hadContext = false
            )
        }


        val filtered = relevant.filter { it.second >= minScore }
        val final = filtered.ifEmpty { emptyList() }

        val hadContext = final.isNotEmpty()

        val prompt = buildRagPrompt(question,  relevant.map { it.first })//or filtered
        val answerText = ask(prompt, askModel)

        val sources = relevant.map { it.first.source }.distinct()

        return RagAnswer(
            answer = answerText,
            sources = sources,
            hadContext = hadContext
        )
    }


    //LLM-Reranker (умно!)
    suspend fun rerankWithLLM(
        question: String,
        candidates: List<ScoredChunk>,  // top-8 после фильтра
        maxReturn: Int = 4,
        askModel: String
    ): List<ScoredChunk> {
        if (candidates.size <= maxReturn) return candidates

        // Формируем компактный список для rerank
        val chunkPreview = candidates.take(8).mapIndexed { i, sc ->
            "[${i}] score=${"%.2f".format(sc.score)}: ${sc.chunk.text.take(200)}..."
        }.joinToString("\n\n")

        val rerankPrompt = """
    Оцени релевантность фрагментов вопросу. Верни ТОП-${maxReturn} индексов 
    (0,1,2...) в порядке убывания полезности для ответа.
    
    ВОПРОС: $question
    
    ФРАГМЕНТЫ:
    $chunkPreview
    
    Ответ: только индексы через запятую (например: "0,2,1")
    """.trimIndent()

        val rerankResult = ask(rerankPrompt, askModel)
        println("🔄 LLM-rerank: $rerankResult")

        // Парсим ответ LLM
        val selectedIndices = rerankResult
            .split(",", " ", "\n")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in candidates.indices }
            .distinct()
            .take(maxReturn)

        return selectedIndices.map { candidates[it] }
    }

    //
    suspend fun answerWithAdvancedRAG(
        question: String,
        index: EmbeddingIndex,
        config: RAGConfig = RAGConfig()
    ): String {
        println("\n🤔 ВОПРОС: $question")

        // Шаг 1: Быстрый ретрив (эмбеддинги)
        val topK = retrieveTopKScoredChunks(question, index, this, config.initialK)
        println("📊 Top-${config.initialK}: scores ${topK.map { "%.2f".format(it.score) }}")

        // Шаг 2: Фильтр по порогу
        val filtered = filterByThreshold(topK, config.minScore)

        // Шаг 3: LLM-rerank (если включен)
        val finalChunks = if (config.useRerank && filtered.size > 1) {
            rerankWithLLM(question, filtered,  config.finalK, config.askModel)
        } else {
            filtered.take(config.finalK)
        }

        println("✅ Финал: ${finalChunks.size} чанков")

        if (finalChunks.isEmpty()) {
            return ask("""
            Нет релевантной информации в документах по вопросу: "$question"
        """.trimIndent(), config.askModel)
        }

        // Шаг 4: Генерация ответа
        val prompt = buildRagPrompt(question, finalChunks.map { it.chunk })
        return ask(prompt, config.askModel)
    }

}