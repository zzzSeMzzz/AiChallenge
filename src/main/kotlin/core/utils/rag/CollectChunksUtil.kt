package core.utils.rag

import core.network.OllamaEmbeddingClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EmbeddedChunk(
    val id: String,
    val source: String,
    val text: String,
    val embedding: List<Float>,
    val tokens: Int
)

suspend fun buildIndexFromDirectory(
    rootDir: String,
    outputFile: String = "index.json"
) {
    val splitter = RecursiveTextSplitter(chunkSize = 512, chunkOverlap = 100)
    splitter.loadTokenizer()

    val ollama = OllamaEmbeddingClient(model = "mxbai-embed-large")

    // 1. Собираем документы
    val chunks = collectChunks(File(rootDir), splitter)
    println("📄 Найдено ${chunks.size} чанков")

    // 2. Батчируем эмбеддинги
    val batchSize = 16  // Ollama хорошо держит батчи до 32
    val embeddedChunks = mutableListOf<EmbeddedChunk>()

    for (batch in chunks.chunked(batchSize)) {
        println("🔄 Эмбеддинги батч ${embeddedChunks.size / batchSize + 1}/${chunks.size / batchSize + 1}")

        val texts = batch.map { it.text }
        val embeddings = ollama.embed(texts)

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

    // 3. Сохраняем индекс
    val index = mapOf(
        "dimension" to embeddedChunks.first().embedding.size,
        "chunks" to embeddedChunks,
        "total_chunks" to embeddedChunks.size
    )

    File(outputFile).writeText(Json { prettyPrint = true }.encodeToString(index))
    println("✅ Индекс сохранён: $outputFile (${embeddedChunks.size} чанков, ${index["dimension"]} dim)")
}

data class Chunk(val id: String, val source: String, val text: String)

fun collectChunks(rootDir: File, splitter: RecursiveTextSplitter): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    var idCounter = 0

    rootDir.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in listOf("txt", "md", "kt", "java", "py", "json") }
        .forEach { file ->
            runBlocking {
                val textChunks = splitter.splitTextWithOverlap(file.readText())
                textChunks.forEach { text ->
                    chunks += Chunk(
                        id = "chunk_${idCounter++}",
                        source = file.relativeTo(rootDir).path,
                        text = text
                    )
                }
            }
        }
    return chunks
}
