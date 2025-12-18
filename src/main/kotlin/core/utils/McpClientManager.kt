package core.utils

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File

object McpClientManager {

    const val WEB_SEARCH_CLIENT = "wbs"
    const val SAVE_TO_FILE_CLIENT = "svtfc"

    val clients = mutableMapOf<String, Client>()
    val transports = mutableMapOf<String, StdioClientTransport>()
    val processes = mutableMapOf<String, Process>()

    fun createWebSearchClient(): Client {
        val process = ProcessBuilder(
            "D:/Program/NodeJS/npx.cmd", "-y", "@guhcostan/web-search-mcp@latest",
            //"D:/projects/java/AiChallenge/MCP_web_search/dist/index.js"
        ).redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        processes[WEB_SEARCH_CLIENT] = process

        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        this.transports[WEB_SEARCH_CLIENT] = transport

        val searchClient = Client(Implementation("web-search-mcp-client", "1.0.0"))
        clients[WEB_SEARCH_CLIENT] = searchClient
        return searchClient
    }

    fun createSavingClient(): Client {
        val process = ProcessBuilder(
            "java", "-jar", "D:/projects/java/AiChallenge/server/build/libs/MPCServer-1.0-SNAPSHOT.jar"
        ).redirectError(ProcessBuilder.Redirect.INHERIT)
            .directory(File("D:/projects/java/AiChallenge/data"))
            .start()

        processes[SAVE_TO_FILE_CLIENT] = process

        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )

        transports[SAVE_TO_FILE_CLIENT] = transport
        val mcpClient = Client(Implementation("kotlin-mcp-client", "1.0.0"))
        clients[SAVE_TO_FILE_CLIENT] = mcpClient
        return mcpClient
    }

    suspend fun closeClients() {
        clients.forEach { (_, client) ->
            client.close()
        }
        processes.forEach { (_, process) ->
            process.destroyForcibly().waitFor()
        }
    }
}