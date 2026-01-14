package core.utils.rag

object TextPreprocessor {



        fun cleanText(text: String): String {
            /*println("🔍 Анализ символов:")
            println("  \\r: ${text.count { it == '\r' }}")
            println("  \\n: ${text.count { it == '\n' }}")
            println("  \\t: ${text.count { it == '\t' }}")*/

            return text
                .replace("\uFEFF", "")           // BOM
                .replace("\r\n", "\n")           // Windows CRLF → LF
                .replace("\r", "\n")             // Mac CR → LF
                .replace(Regex("\\n{3,}"), "\n\n") // 3+ \n → 2 \n
                .replace(Regex("[ \t]+"), " ")    // табы + множественные пробелы → 1 пробел
                .lines()                         // разбиваем по строкам
                .map { it.trim() }               // trim каждой строки
                .filter { it.isNotBlank() }      // убираем пустые
                .joinToString("\n")              // собираем обратно
                .trim()                          // финальный trim
        }


    fun normalizeMarkdown(text: String): String {
        return cleanText(text)
            // ✅ Markdown: нормализуем пробелы после #, -, *
            .replace(Regex("(#{1,6})\\s+"), "$1 ")
            .replace(Regex("^-\\s+"), "- ")
            .replace(Regex("^\\*\\s+"), "* ")
    }
}
