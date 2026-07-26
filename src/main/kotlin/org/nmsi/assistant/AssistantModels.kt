package org.nmsi.assistant

import kotlinx.serialization.Serializable

@Serializable
data class FacilityRecommendation(
    val id: Long,
    val name: String,
    val reason: String,
    val detailUrl: String,
)

@Serializable
data class AssistantReply(
    val text: String,
    val recommendations: List<FacilityRecommendation> = emptyList(),
    val mode: String,
)

interface SupportAssistant {
    suspend fun respond(
        userId: Long,
        message: String,
    ): AssistantReply
}

