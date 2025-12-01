package core.data

data class YaGptResponse(
    val result: Result
)

data class Result(
    val alternatives: List<Alternative>,
    val usage: Usage
)

data class Alternative(
    val message: ChatMessage,
    val status: String
)

data class Usage(
    val inputTextTokens: String,
    val outputTextTokens: String,
    val totalTokens: String
)