package core.data.base

import core.utils.AiAnswer

interface LlmClient {
    suspend fun chat(messages: List<ChatMessage>): AiAnswer?
}