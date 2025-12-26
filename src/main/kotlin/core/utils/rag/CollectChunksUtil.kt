package core.utils.rag

import core.data.base.Chunk
import core.data.base.EmbeddedChunk
import core.data.base.EmbeddingIndex
import core.network.OllamaClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File



suspend fun buildIndexFromDirectory(
    rootDir: String,
    model: String,
    outputFile: String = "index.json"
) {
   // val splitter = RecursiveTextSplitter(chunkSize = 512, chunkOverlap = 100)

    val splitter = RecursiveTextSplitter(
        chunkSize = 300,           // ✅ Меньше!
        chunkOverlap = 50,
        maxContextTokens = 512    // ✅ Лимит mxbai-embed-large
    )
    splitter.loadTokenizer()

    //val ollama = OllamaEmbeddingClient(model = "mxbai-embed-large")
    val ollama = OllamaClient(defaultModel = model)

    // 1. Собираем документы
    val chunks = collectChunks(File(rootDir), splitter)
    println("📄 Найдено ${chunks.size} чанков")

    // 2. Батчируем эмбеддинги
    val batchSize = 16  // Ollama хорошо держит батчи до 32
    val embeddedChunks = mutableListOf<EmbeddedChunk>()

    for (batch in chunks.chunked(batchSize)) {
        println("🔄 Эмбеддинги батч ${embeddedChunks.size / batchSize + 1}/${chunks.size / batchSize + 1}")

        val texts = batch.map { it.text }
        println("📝 Обрабатываем ${texts.size} чанков")
        val embeddings = ollama.embed(texts, "mxbai-embed-large")

        println("embeddings size: ${embeddings.size}")

        batch.zip(embeddings).forEachIndexed { idx, (chunk, embedding) ->
            embeddedChunks += EmbeddedChunk(
                id = chunk.id,
                source = chunk.source,
                text = chunk.text,
                embedding = embedding,
                tokens = splitter.countTokens(chunk.text)
            )
        }
    }

    println("✅ Эмбеддинги собраны")
    // 3. Сохраняем индекс
    val index = EmbeddingIndex(
        dimension = embeddedChunks.first().embedding.size,
        chunks =  embeddedChunks,
        totalChunks = embeddedChunks.size
    )

    File(outputFile).writeText(Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(index))
    println("✅ Индекс сохранён: $outputFile (${embeddedChunks.size} чанков, ${index.dimension} dim)")
}


fun collectChunks(rootDir: File, splitter: RecursiveTextSplitter): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    var idCounter = 0

    rootDir.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in listOf("txt", "md",) }
        .forEach { file ->
            println("📄 Обрабатываем: ${file.name}")

            val rawText = file.readText(Charsets.UTF_8)
            val cleanText = TextPreprocessor.normalizeMarkdown(rawText)

            println("   Исходный: ${rawText.length} символов → Очищенный: ${cleanText.length}")

            runBlocking { // ✅ Чистый текст!
                val textChunks = splitter.splitTextWithLimits(cleanText)
                textChunks.forEach { text ->
                    val cleanedChunk = TextPreprocessor.cleanText(text)  // Двойная страховка
                    chunks += Chunk(
                        id = "chunk_${idCounter++}",
                        source = file.relativeTo(rootDir).path,
                        text = cleanedChunk
                    )
                }
            }
        }
    return chunks
}

