package core.utils.rag

import com.aallam.ktoken.Encoding
import com.aallam.ktoken.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recursive character / token splitter
 * Алгоритм: пытается сначала резать по крупным разделителям (двойной перенос — параграф),
 * потом по одиночному переносу (строка), потом по пробелам, и только в конце по «сырым» символам,
 * пока не уложится в лимит размера.
 *
 * Плюсы: хороший компромисс — пытается сохранить целые абзацы/предложения, но гарантирует, что чанки не больше нужного размера.
 *
 * Минусы: чуть сложнее и медленнее, чем тупо по длине.
 */


class RecursiveTextSplitter(
    private val chunkSize: Int = 100,
    private val chunkOverlap: Int = 20,
    private val separators: List<String> = listOf(
        "\n\n", "\n", ". ", " ", ""
    )
) {
    lateinit var tokenizer: Tokenizer

    suspend fun loadTokenizer() {
        tokenizer = Tokenizer.of(encoding = Encoding.CL100K_BASE)
    }

    suspend fun countTokens(text: String): Int {
        return withContext(Dispatchers.Default) {
            tokenizer.encode(text).size
        }
    }

    // ✅ КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: синхронный merge с ожиданием токенов
    private suspend fun mergeChunks(
        splits: List<String>,
        separator: String
    ): List<String> {
        val chunks = mutableListOf<String>()
        if (splits.isEmpty()) return chunks

        var currentChunk = splits.first()

        for (split in splits.drop(1)) {
            val candidate = buildString {
                append(currentChunk)
                if (separator.isNotEmpty()) append(separator)
                append(split)
            }

            // ✅ Ждём точный подсчёт токенов
            val candidateTokens = countTokens(candidate)
            if (candidateTokens < chunkSize) {
                currentChunk = candidate
            } else {
                chunks.add(currentChunk)
                currentChunk = split
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }
        return chunks
    }

    suspend fun splitText(text: String): List<String> {
        println("🔍 Исходный текст: ${countTokens(text)} tokens")
        return splitTextWithSeparators(text, ArrayList(separators))
    }

    private suspend fun splitTextWithSeparators(
        text: String,
        separators: MutableList<String>
    ): List<String> {
        if (separators.isEmpty()) {
            val chunks = text.chunked(chunkSize)
            println("📄 Fallback chunked: ${chunks.size} chunks")
            return chunks
        }

        val separator = separators.removeFirst()
        val splits = if (separator.isNotEmpty()) {
            text.split(separator)
        } else {
            text.chunked(chunkSize)
        }

        println("✂️  Разделитель '$separator': ${splits.size} частей")

        val merged = mergeChunks(splits, separator)

        // ✅ Проверяем ТОЛЬКО после подсчёта токенов
        val allSmall = merged.all { countTokens(it) < chunkSize }
        println("✅ Все чанки малы? $allSmall (макс: ${merged.maxOf { countTokens(it) }} tokens)")

        if (allSmall) {
            return merged
        }

        // Рекурсивно разбиваем большие чанки
        val result = mutableListOf<String>()
        for (chunk in merged) {
            val chunkTokens = countTokens(chunk)
            if (chunkTokens < chunkSize) {
                result.add(chunk)
            } else {
                println("🔄 Рекурсия для чанка ${chunk.take(50)}... ($chunkTokens tokens)")
                result.addAll(splitTextWithSeparators(chunk, ArrayList(separators)))
            }
        }
        return result
    }

    // ... остальной код без изменений (tokenizer, mergeChunks, splitTextWithSeparators)

    // ✅ НОВЫЙ МЕТОД: с overlap
    suspend fun splitTextWithOverlap(text: String): List<String> {
        val rawChunks = splitText(text)  // базовое разбиение

        if (rawChunks.size <= 1) return rawChunks

        val overlappedChunks = mutableListOf<String>()

        rawChunks.forEachIndexed { index, chunk ->
            // Добавляем текущий чанк
            overlappedChunks.add(chunk)

            // Добавляем overlap с следующим чанком
            if (index < rawChunks.lastIndex) {
                val nextChunk = rawChunks[index + 1]

                // Вычисляем размер overlap в токенах
                val overlapTokens = minOf(chunkOverlap, chunkSize / 5)

                val currentTokens = countTokens(chunk)
                val overlapSize = (overlapTokens * 4).coerceAtMost(chunk.length / 2)  // ~символы

                if (overlapSize > 0 && chunk.length > overlapSize) {
                    val overlappedChunk = chunk.dropLast(overlapSize) + nextChunk.take(overlapSize)

                    // Проверяем, что overlapped чанк не превышает лимит
                    val overlappedTokens = countTokens(overlappedChunk)
                    if (overlappedTokens <= chunkSize) {
                        overlappedChunks.add(overlappedChunk)
                    }
                }
            }
        }

        return overlappedChunks
    }


}

