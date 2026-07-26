package org.nmsi.assistant

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.nmsi.data.Facility
import org.nmsi.data.SupportRepository
import java.time.OffsetDateTime

internal class AssistantToolExecutor(
    private val repository: SupportRepository,
    private val json: Json,
) {
    fun execute(
        userId: Long,
        name: String,
        arguments: JsonObject,
    ): AssistantToolExecution =
        when (name) {
            "list_categories" -> listCategories()
            "search_facilities" -> searchFacilities(arguments)
            "get_facility" -> getFacility(arguments)
            "list_user_appointments" -> listAppointments(userId)
            "get_available_slots" -> getAvailableSlots(arguments)
            "book_appointment" -> bookAppointment(userId, arguments)
            "cancel_appointment" -> cancelAppointment(userId, arguments)
            else -> error("unsupported_tool", "The requested tool is not supported.")
        }

    private fun listCategories(): AssistantToolExecution {
        val categories =
            repository.listCategories().map { category ->
                mapOf(
                    "slug" to JsonPrimitive(category.slug),
                    "name" to JsonPrimitive(category.name),
                    "description" to JsonPrimitive(category.description),
                    "keywords" to JsonPrimitive(category.keywords),
                )
            }
        return AssistantToolExecution(
            output =
                buildJsonObject {
                    put("result_count", JsonPrimitive(categories.size))
                    put("categories", Json.parseToJsonElement(json.encodeToString(categories)))
                }.toString(),
        )
    }

    private fun searchFacilities(arguments: JsonObject): AssistantToolExecution {
        val query = arguments.string("query").orEmpty()
        val requestedCategory =
            arguments
                .string("category_slug")
                .orEmpty()
                .trim()
                .lowercase()
        val categories = repository.listCategories()
        val appliedCategory = categories.firstOrNull { category -> category.slug == requestedCategory }
        val facilities =
            repository
                .searchFacilities(query, appliedCategory?.slug)
                .take(MAX_FACILITY_RESULTS)
        val categoryNote =
            when {
                requestedCategory.isBlank() -> "No category filter was requested."
                appliedCategory == null ->
                    "The requested category slug is not in the NMSI taxonomy, so the search was safely retried without a category filter."
                else -> "The verified category filter '${appliedCategory.slug}' was applied."
            }
        return AssistantToolExecution(
            output =
                buildJsonObject {
                    put("query", JsonPrimitive(query))
                    put("requested_category_slug", JsonPrimitive(requestedCategory))
                    put("applied_category_slug", JsonPrimitive(appliedCategory?.slug.orEmpty()))
                    put("category_note", JsonPrimitive(categoryNote))
                    put("result_count", JsonPrimitive(facilities.size))
                    put(
                        "facilities",
                        Json.parseToJsonElement(json.encodeToString(facilities.map(::facilityPayload))),
                    )
                    if (facilities.isEmpty()) {
                        put(
                            "next_action",
                            JsonPrimitive(
                                "Ask one short clarification or call list_categories. Do not claim the NMSI directory is empty.",
                            ),
                        )
                    }
                }.toString(),
            facilities = facilities,
        )
    }

    private fun getFacility(arguments: JsonObject): AssistantToolExecution {
        val facility = arguments.long("facility_id")?.let(repository::facilityById)
        return if (facility == null) {
            error("facility_not_found", "The facility id was not found.")
        } else {
            AssistantToolExecution(
                output = json.encodeToString(facilityPayload(facility)),
                facilities = listOf(facility),
            )
        }
    }

    private fun listAppointments(userId: Long): AssistantToolExecution =
        AssistantToolExecution(
            output =
                buildJsonObject {
                    val appointments =
                        repository.listAppointments(userId).map { appointment ->
                            mapOf(
                                "appointment_id" to JsonPrimitive(appointment.id),
                                "facility_id" to JsonPrimitive(appointment.facilityId),
                                "facility_name" to JsonPrimitive(appointment.facilityName),
                                "starts_at" to JsonPrimitive(appointment.startsAt.toString()),
                                "status" to JsonPrimitive(appointment.status),
                                "note" to JsonPrimitive(appointment.note.orEmpty()),
                            )
                        }
                    put("result_count", JsonPrimitive(appointments.size))
                    put("appointments", Json.parseToJsonElement(json.encodeToString(appointments)))
                }.toString(),
        )

    private fun getAvailableSlots(arguments: JsonObject): AssistantToolExecution {
        val facilityId = arguments.long("facility_id")
        val facility = facilityId?.let(repository::facilityById)
        if (facility == null) return error("facility_not_found", "Choose a facility returned by search_facilities.")

        val slots =
            repository.availableSlots(facility.id).map { slot ->
                mapOf(
                    "slot_id" to JsonPrimitive(slot.id),
                    "facility_id" to JsonPrimitive(slot.facilityId),
                    "starts_at" to JsonPrimitive(slot.startsAt.toString()),
                )
            }
        return AssistantToolExecution(
            output =
                buildJsonObject {
                    put("facility_id", JsonPrimitive(facility.id))
                    put("facility_name", JsonPrimitive(facility.name))
                    put("result_count", JsonPrimitive(slots.size))
                    put("available_slots", Json.parseToJsonElement(json.encodeToString(slots)))
                }.toString(),
            facilities = listOf(facility),
        )
    }

    private fun bookAppointment(
        userId: Long,
        arguments: JsonObject,
    ): AssistantToolExecution {
        val facilityId = arguments.long("facility_id")
        val facility = facilityId?.let(repository::facilityById)
        val startsAt = arguments.string("starts_at")?.let { value -> runCatching { OffsetDateTime.parse(value) }.getOrNull() }
        return when {
            arguments.boolean("confirmed") != true ->
                error(
                    "confirmation_required",
                    "State the facility and exact available time, then obtain explicit student confirmation before booking.",
                )
            facility == null || startsAt == null ->
                error("invalid_booking_request", "Use a facility and exact time returned by the database tools.")
            else ->
                runCatching {
                    repository.bookAppointment(
                        userId = userId,
                        facilityId = facility.id,
                        startsAt = startsAt,
                        note = arguments.string("note")?.takeIf(String::isNotBlank),
                    )
                }.fold(
                    onSuccess = { appointment ->
                        AssistantToolExecution(
                            output =
                                buildJsonObject {
                                    put("appointment_id", JsonPrimitive(appointment.id))
                                    put("facility_id", JsonPrimitive(appointment.facilityId))
                                    put("facility_name", JsonPrimitive(appointment.facilityName))
                                    put("starts_at", JsonPrimitive(appointment.startsAt.toString()))
                                    put("status", JsonPrimitive(appointment.status))
                                }.toString(),
                            facilities = listOf(facility),
                        )
                    },
                    onFailure = {
                        error(
                            "slot_unavailable",
                            "That time is no longer available. Call get_available_slots again before offering another time.",
                        )
                    },
                )
        }
    }

    private fun cancelAppointment(
        userId: Long,
        arguments: JsonObject,
    ): AssistantToolExecution {
        if (arguments.boolean("confirmed") != true) {
            return error(
                "confirmation_required",
                "Identify the booked appointment and obtain explicit student confirmation before cancellation.",
            )
        }
        val appointmentId = arguments.long("appointment_id")
        val appointment = appointmentId?.let { id -> repository.cancelAppointment(userId, id) }
        return if (appointment == null) {
            error(
                "appointment_not_found",
                "A booked appointment with that id was not found for the signed-in student.",
            )
        } else {
            AssistantToolExecution(
                output =
                    buildJsonObject {
                        put("appointment_id", JsonPrimitive(appointment.id))
                        put("facility_id", JsonPrimitive(appointment.facilityId))
                        put("facility_name", JsonPrimitive(appointment.facilityName))
                        put("starts_at", JsonPrimitive(appointment.startsAt.toString()))
                        put("status", JsonPrimitive(appointment.status))
                    }.toString(),
            )
        }
    }

    private fun facilityPayload(facility: Facility): Map<String, JsonElement> =
        mapOf(
            "id" to JsonPrimitive(facility.id),
            "name" to JsonPrimitive(facility.name),
            "scope" to JsonPrimitive(facility.summary),
            "provider" to JsonPrimitive(facility.provider),
            "location" to JsonPrimitive(facility.location),
            "distance_minutes" to JsonPrimitive(facility.distanceMinutes),
            "rating" to JsonPrimitive(facility.rating),
            "response_time" to JsonPrimitive(facility.responseTime),
            "contact_mode" to JsonPrimitive(facility.contactMode),
            "eligibility" to JsonPrimitive(facility.eligibility),
            "preparation" to JsonPrimitive(facility.preparation),
            "categories" to JsonArray(facility.categories.map { category -> JsonPrimitive(category.name) }),
            "available_slots" to
                JsonArray(
                    repository.availableSlots(facility.id).map { slot ->
                        JsonPrimitive(slot.startsAt.toString())
                    },
                ),
        )

    private fun error(
        code: String,
        message: String,
    ) = AssistantToolExecution(
        output =
            buildJsonObject {
                put("error", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            }.toString(),
    )

    private fun JsonObject.string(name: String): String? =
        this[name]
            ?.jsonPrimitive
            ?.contentOrNull

    private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()

    private fun JsonObject.boolean(name: String): Boolean? =
        string(name)?.let { value ->
            when (value.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

    private companion object {
        const val MAX_FACILITY_RESULTS = 6
    }
}

internal data class AssistantToolExecution(
    val output: String,
    val facilities: List<Facility> = emptyList(),
)
