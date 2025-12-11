package core.utils

import core.data.base.ChatMessage
import core.network.Client
import core.network.PerClient

object ClientManager {

    private var yaClient: Client? = null
    private var perClient: PerClient? = null

    suspend fun ask(
        client: AiClientType,
        messages: List<ChatMessage>,
        model: String? = null,
        temperature: Double = 0.4,
        maxTokens: Int = 512,
    ): AiAnswer? {
        return when (client) {
            AiClientType.PERPLEXITY -> {
                val answer = PerClient.askPerplexity(
                    messages = messages.map {
                        it.toPerplexity()
                    },
                    temperature = temperature,
                    model = model ?: "sonar",
                    maxTokens = maxTokens,
                )
                answer
            }
            AiClientType.YANDEX_GPT -> {

                val answer = Client.askYaGpt(
                    messages = messages.map { it.toYa() },
                    temperature = temperature,
                    model = model ?: "yandexgpt-lite",
                    maxTokens = maxTokens,
                )

                answer
            }
        }
    }

    fun close() {
        yaClient?.close()
        perClient?.close()
    }
}


interface AiAnswer {
    fun answer(): String
    fun totalTokens(): Int
    fun totalPrice(): Double
    fun completionTokens(): Int
    fun promptTokens(): Int
}

enum class AiClientType {
    PERPLEXITY, YANDEX_GPT
}