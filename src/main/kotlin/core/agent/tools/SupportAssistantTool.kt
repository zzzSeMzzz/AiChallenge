package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.data.base.SupportTicket
import core.network.OllamaClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SupportAnswerPayload(
    val question: String,
    val ticket: SupportTicket?,
    val docs_context: String,
    val answer: String
)

class SupportAssistantTool(
    private val supportRagTool: SupportRagTool,
    private val crmTool: CrmTicketsTool,
    private val llm: OllamaClient  // или твой абстрактный LlmClient
) : Tool {
    override val name = "support_assistant"
    override val description = "Answer user support questions using docs + CRM ticket context"
    override val parameters = ToolParameters(
        schema = mapOf(
            "question" to "string: user question",
            "ticket_id" to "string? - optional",
            "email" to "string? - optional"
        )
    )

    private val json = Json { ignoreUnknownKeys = true }


    override suspend fun execute(ctx: ToolContext): ToolResult {
        val question = ctx.args["question"] as? String
            ?: return ToolResult.Error("Missing question")

        val ticketId = ctx.args["ticket_id"] as? String
        val email = ctx.args["email"] as? String

        // 1. RAG по документации
        val ragRes = supportRagTool.execute(
            ToolContext(
                args = mapOf("query" to question, "top_k" to 5),
                session = ctx.session
            )
        )
        val docsContext = (ragRes as? ToolResult.Ok)?.content ?: ""

        // 2. CRM тикет
        val ticketJson = (crmTool.execute(
            ToolContext(
                args = mapOf("ticket_id" to ticketId, "email" to email),
                session = ctx.session
            )
        ) as? ToolResult.Ok)?.content ?: """{"found": false}"""

        val ticket = try {
            json.decodeFromString<SupportTicket>(ticketJson)
        } catch (_: Exception) {
            null
        }

        // 3. Промпт для LLM
        val prompt = buildString {
            appendLine("Ты — ассистент поддержки пользователей этого продукта.")
            appendLine("Используй FAQ/документацию и данные тикета, чтобы ответить на вопрос.")
            appendLine()
            appendLine("ВОПРОС ПОЛЬЗОВАТЕЛЯ:")
            appendLine(question)
            appendLine()
            appendLine("КОНТЕКСТ ТИКЕТА:")
            if (ticket != null) {
                appendLine("id: ${ticket.id}")
                appendLine("email: ${ticket.email}")
                appendLine("subject: ${ticket.subject}")
                appendLine("status: ${ticket.status}")
                ticket.lastError?.let { appendLine("last_error: $it") }
                ticket.platform?.let { appendLine("platform: $it") }
            } else {
                appendLine("(тикет не найден)")
            }
            appendLine()
            appendLine("КОНТЕКСТ ДОКУМЕНТАЦИИ / FAQ:")
            appendLine(docsContext.take(4000))
            appendLine()
            appendLine("Ответь кратко и по делу, по-русски. Если информации недостаточно, честно скажи об этом и предложи запросить доп. данные.")
        }

        val answerText = llm.ask(prompt, model = "qwen2.5:3b")

        val payload = SupportAnswerPayload(
            question = question,
            ticket = ticket,
            docs_context = docsContext,
            answer = answerText
        )
        val resultJson = json.encodeToString(payload)

        return ToolResult.Ok(resultJson)
    }
}
