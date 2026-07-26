package org.nmsi.assistant

import org.nmsi.data.Facility
import org.nmsi.data.SupportRepository

class RuleBasedSupportAssistant(
    private val repository: SupportRepository,
) : SupportAssistant {
    override suspend fun respond(
        userId: Long,
        message: String,
    ): AssistantReply {
        val normalized = message.lowercase()
        repository.appendConversationMessage(userId, "USER", message)

        val route = ROUTES.firstOrNull { candidate -> candidate.keywords.any(normalized::contains) }
        val matchedKeyword = route?.keywords?.firstOrNull(normalized::contains)
        val facilities =
            when {
                normalized.contains("appointment") || normalized.contains("booking") ->
                    emptyList()
                route != null ->
                    repository
                        .searchFacilities(query = matchedKeyword, categorySlug = route.categorySlug)
                        .ifEmpty { repository.searchFacilities(query = null, categorySlug = route.categorySlug) }
                        .take(3)
                else -> repository.searchFacilities(query = null, categorySlug = null).take(3)
            }
        val text =
            when {
                normalized.contains("appointment") || normalized.contains("booking") ->
                    appointmentSummary(userId)
                route != null ->
                    "Your description may relate to ${route.label}. This is initial guidance rather than a diagnosis. I found a few sources that cover this area, including overlapping responsibilities, so compare their scope and access details before choosing."
                else ->
                    "I am not yet certain which need area fits best. You do not need to choose a university category yourself. Could you tell me what happened, what you need to do next, or whether time, location or privacy matters most?"
            }
        repository.appendConversationMessage(userId, "ASSISTANT", text)
        return AssistantReply(
            text = text,
            recommendations = facilities.map(::recommendation),
            mode = "database-guided fallback",
        )
    }

    private fun appointmentSummary(userId: Long): String {
        val appointments = repository.listAppointments(userId).filter { it.status == "BOOKED" }
        if (appointments.isEmpty()) {
            return "You have no booked support appointments. Tell me what you need help with and I can suggest a source; " +
                "without a DeepSeek API key, complete booking actions through the facility page."
        }
        val summary =
            appointments.joinToString("; ") { appointment ->
                "#${appointment.id} ${appointment.facilityName} at ${appointment.startsAt}"
            }
        return "Your booked appointment(s): $summary. You can review or cancel them in My appointments."
    }

    private fun recommendation(facility: Facility) =
        FacilityRecommendation(
            id = facility.id,
            name = facility.name,
            reason = "${facility.responseTime}; ${facility.contactMode}.",
            detailUrl = "/support/${facility.id}",
        )

    private data class Route(
        val categorySlug: String,
        val label: String,
        val keywords: Set<String>,
    )

    private companion object {
        val ROUTES =
            listOf(
                Route(
                    "safety-urgent",
                    "urgent or safety support",
                    setOf("danger", "unsafe", "assault", "harassment", "emergency", "crisis"),
                ),
                Route(
                    "academic-study",
                    "academic study or assessment support",
                    setOf("deadline", "extension", "assessment", "assignment", "study", "exam", "statistics"),
                ),
                Route(
                    "wellbeing",
                    "wellbeing or emotional support",
                    setOf("stress", "anxiety", "low mood", "lonely", "homesick", "overwhelmed", "relationship"),
                ),
                Route(
                    "digital-access",
                    "digital access or IT support",
                    setOf("laptop", "wifi", "password", "software", "device", "account", "printing"),
                ),
                Route(
                    "money-funding",
                    "money or funding advice",
                    setOf("money", "fee", "rent", "debt", "budget", "hardship", "funding"),
                ),
                Route(
                    "programme-choices",
                    "programme or course choices",
                    setOf("module", "course", "programme", "pathway", "transfer", "prerequisite"),
                ),
                Route(
                    "disability-access",
                    "disability and accessibility support",
                    setOf("disability", "adjustment", "dyslexia", "adhd", "autism", "mobility", "sensory"),
                ),
                Route(
                    "international-visa",
                    "international or visa advice",
                    setOf("visa", "immigration", "international", "right to study", "passport"),
                ),
                Route(
                    "careers-work",
                    "careers and employment",
                    setOf("career", "job", "cv", "interview", "placement", "internship"),
                ),
                Route(
                    "housing-campus-life",
                    "housing or campus life",
                    setOf("housing", "accommodation", "flatmate", "residence", "tenancy", "commute"),
                ),
            )
    }
}
