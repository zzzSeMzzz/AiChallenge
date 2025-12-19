

fun String.trimUntilKeyword(keyword: String): String {
    val index = this.indexOf(keyword)
    return if (index != -1) {
        this.substring(0, index + keyword.length)
    } else {
        this // Возвращаем исходную строку, если слово не найдено
    }
}