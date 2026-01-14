package core.agent.tools

import core.agent.base.Tool
import core.agent.base.ToolContext
import core.agent.base.ToolParameters
import core.agent.base.ToolResult
import core.data.base.SupportTicket
import kotlinx.serialization.json.Json
import java.io.File

class CrmTicketsTool(
    private val ticketsFile: File = File("support_tickets.json")
) : Tool {
    override val name = "crm_find_ticket"
    override val description =
        "Search support tickets by email and/or question text (subject/last_error match)."
    override val parameters = ToolParameters(
        schema = mapOf(
            "email" to "string? - user email (optional)",
            "query" to "string? - question text to match subject/last_error (optional)"
        )
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTickets(): List<SupportTicket> =
        json.decodeFromString(ticketsFile.readText())

    override suspend fun execute(ctx: ToolContext): ToolResult {
        val email = ctx.args["email"] as? String
        val query = (ctx.args["query"] as? String)?.lowercase()

        val tickets = loadTickets()

        val filtered = tickets.filter { t ->
            val byEmail = email?.let { t.email.equals(it, ignoreCase = true) } ?: true
            val byQuery = query?.let { q ->
                t.subject.lowercase().contains(q) ||
                        (t.lastError?.lowercase()?.contains(q) ?: false)
            } ?: true
            byEmail && byQuery
        }

        // Вернём все подходящие тикеты как JSON-массив
        val resultJson = json.encodeToString(filtered)
        return ToolResult.Ok(resultJson)
    }
}
