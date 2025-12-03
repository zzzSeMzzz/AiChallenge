package core.data.perplexety

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PerMessage(
    val role: String,
    val content: String
) {
    companion object {
        fun system(text: String) = PerMessage("system", text)

        fun assistant(text: String) = PerMessage("assistant", text)

        fun user(text: String) = PerMessage("user", text)
    }
}

@Serializable
data class ResponseFormat(
    val type: String,
    @SerialName("json_schema")
    val jsonSchema: JsonSchema
)

@Serializable
data class JsonSchema(
    val schema: JsonObject
)

@Serializable
data class PerplexityRequest(
    val model: String,
    val messages: List<PerMessage>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null
)

@Serializable
data class Choice(
    val message: PerMessage,
    val index: Int? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class PerplexityResponse(
    val choices: List<Choice>,
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null
)