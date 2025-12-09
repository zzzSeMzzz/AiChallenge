package core.utils

class ClientManager {

    val clients = mutableListOf<AiClient>()
}


interface AiAnswer {
    fun answer(): String
    fun totalTokens(): Int
    fun totalPrice(): Double
}

interface AiMessage {}

interface AiClient {


    suspend fun askAi(
        messages: MutableList<AiMessage>,
        temperature: Double = 0.4,
        model: String
    ): AiAnswer

    fun close()
}