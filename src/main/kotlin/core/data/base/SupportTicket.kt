package core.data.base

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupportTicket(
    val id: String?,
    @SerialName("user_id")
    val userId: String,
    val email: String,
    val subject: String,
    val status: String,
    @SerialName("last_error")
    val lastError: String? = null,
    val platform: String? = null,
    @SerialName("created_at")
    val createdAt: String
)