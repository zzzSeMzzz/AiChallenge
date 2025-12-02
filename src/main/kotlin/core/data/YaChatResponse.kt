package core.data

import kotlinx.serialization.Serializable

@Serializable
data class YaGptResponse(
    val result: Result
)

@Serializable
data class Result(
    val alternatives: List<Alternative>,
    val usage: Usage
)

@Serializable
data class Alternative(
    val message: ChatMessage,
    val status: String
)

@Serializable
data class Usage(
    val inputTextTokens: String,
    val outputTextTokens: String,
    val totalTokens: String
)