package core.data.base

import kotlinx.serialization.Serializable

@Serializable
data class SerializableSummary(
    val id: Long,
    val text: String
)

@Serializable
data class SerializableMemoryState(
    val summaries: List<SerializableSummary>,
    val lastSummaryId: Long
)