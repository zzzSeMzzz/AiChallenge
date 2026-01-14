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
    override val name = "crm_get_ticket"
    override val description = "Get support ticket by id or email"
    override val parameters = ToolParameters(
        schema = mapOf(
            "ticket_id" to "string? - optional",
            "email" to "string? - optional"
        )
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTickets(): List<SupportTicket> =
        json.decodeFromString(ticketsFile.readText())

    override suspend fun execute(ctx: ToolContext): ToolResult {
        val ticketId = ctx.args["ticket_id"] as? String
        val email = ctx.args["email"] as? String

        val tickets = loadTickets()
        val ticket = when {
            ticketId != null -> tickets.find { it.id == ticketId }
            email != null -> tickets.find { it.email.equals(email, ignoreCase = true) }
            else -> null
        } ?: return ToolResult.Ok("""{"found": false}""")

        return ToolResult.Ok(json.encodeToString(ticket))
    }
}