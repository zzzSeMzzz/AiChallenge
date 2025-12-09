package core.utils

import core.data.perplexety.PerMessage
import core.data.ya.ChatMessage
import core.network.Client
import core.network.PerClient

object ClientManager {

    private var yaClient: Client? = null
    private var perClient: PerClient? = null

    val messagesPer = mutableListOf<PerMessage>()/*.apply {
        add(PerMessage.system("Ты сценарист"))
    }*/
    val messagesYa = mutableListOf<ChatMessage>()/*.apply {
        add(ChatMessage.system("Ты сценарист"))
    }*/

    suspend fun ask(
        client: AiClientType,
        input: String,
        isSystemPrompt: Boolean = false,
        model: String? = null,
        temperature: Double = 0.4
    ): AiAnswer? {
        return when (client) {
            AiClientType.PERPLEXITY -> {
                if(isSystemPrompt) {
                    if(messagesPer.firstOrNull()?.role == "system") {
                        messagesPer.removeAt(0)
                    }
                    messagesPer.add(0, PerMessage.system(input))
                    null
                }
                messagesPer.add(PerMessage.user(input))

                val answer = PerClient.askPerplexity(
                    messages = messagesPer,
                    temperature = temperature,
                    model = model ?: "sonar",
                )
                messagesPer.add(PerMessage.assistant(answer.answer()))
                answer
            }
            AiClientType.YANDEX_GPT -> {
                if(isSystemPrompt) {
                    if(messagesYa.firstOrNull()?.role == "system") {
                        messagesYa.removeAt(0)
                    }
                    messagesYa.add(0, ChatMessage.system(input))
                    return null
                }

                messagesYa.add(ChatMessage.user(input))

                val answer = Client.askYaGpt(
                    messages = messagesYa,
                    temperature = temperature,
                    model = model ?: "yandexgpt-lite",
                )

                messagesYa.add(ChatMessage.assistant(answer.answer()))
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
}

enum class AiClientType {
    PERPLEXITY, YANDEX_GPT
}