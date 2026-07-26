package org.nmsi.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.nmsi.data.DatabaseFactory
import org.nmsi.data.SupportRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistantToolExecutorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `facility tool retries natural language safely when a category slug is unknown`() {
        database().use { dataSource ->
            val repository = SupportRepository(dataSource)
            repository.ensureFutureSlots()
            val executor = AssistantToolExecutor(repository, json)

            val result =
                executor.execute(
                    userId = repository.demoUser().id,
                    name = "search_facilities",
                    arguments =
                        buildJsonObject {
                            put(
                                "query",
                                JsonPrimitive(
                                    "tutoring, study skills, exam preparation, or learning development",
                                ),
                            )
                            put("category_slug", JsonPrimitive("academic-support"))
                        },
                )
            val payload = json.parseToJsonElement(result.output).jsonObject

            assertTrue(
                payload
                    .getValue("result_count")
                    .jsonPrimitive.content
                    .toInt() > 0,
            )
            assertEquals("", payload.getValue("applied_category_slug").jsonPrimitive.content)
            assertTrue(result.facilities.any { facility -> facility.name == "Academic Skills Hub" })
        }
    }

    @Test
    fun `appointment tools require confirmation and can book list and cancel for the signed in user`() {
        database().use { dataSource ->
            val repository = SupportRepository(dataSource)
            repository.ensureFutureSlots()
            val executor = AssistantToolExecutor(repository, json)
            val user = repository.demoUser()
            val facility = repository.searchFacilities("study skills", null).first()
            val slot = repository.availableSlots(facility.id).first()

            val unconfirmedBooking =
                executor.execute(
                    user.id,
                    "book_appointment",
                    buildJsonObject {
                        put("facility_id", JsonPrimitive(facility.id))
                        put("starts_at", JsonPrimitive(slot.startsAt.toString()))
                        put("note", JsonPrimitive(""))
                        put("confirmed", JsonPrimitive(false))
                    },
                )
            assertEquals(
                "confirmation_required",
                json
                    .parseToJsonElement(unconfirmedBooking.output)
                    .jsonObject
                    .getValue("error")
                    .jsonPrimitive.content,
            )

            val booking =
                executor.execute(
                    user.id,
                    "book_appointment",
                    buildJsonObject {
                        put("facility_id", JsonPrimitive(facility.id))
                        put("starts_at", JsonPrimitive(slot.startsAt.toString()))
                        put("note", JsonPrimitive("Exam preparation"))
                        put("confirmed", JsonPrimitive(true))
                    },
                )
            val bookedPayload = json.parseToJsonElement(booking.output).jsonObject
            val appointmentId =
                bookedPayload
                    .getValue("appointment_id")
                    .jsonPrimitive.content
                    .toLong()
            assertEquals("BOOKED", bookedPayload.getValue("status").jsonPrimitive.content)

            val appointments = executor.execute(user.id, "list_user_appointments", buildJsonObject {})
            assertTrue(appointments.output.contains("\"appointment_id\":$appointmentId"))

            val unconfirmedCancellation =
                executor.execute(
                    user.id,
                    "cancel_appointment",
                    buildJsonObject {
                        put("appointment_id", JsonPrimitive(appointmentId))
                        put("confirmed", JsonPrimitive(false))
                    },
                )
            assertEquals(
                "confirmation_required",
                json
                    .parseToJsonElement(unconfirmedCancellation.output)
                    .jsonObject
                    .getValue("error")
                    .jsonPrimitive.content,
            )

            val cancellation =
                executor.execute(
                    user.id,
                    "cancel_appointment",
                    buildJsonObject {
                        put("appointment_id", JsonPrimitive(appointmentId))
                        put("confirmed", JsonPrimitive(true))
                    },
                )
            assertEquals(
                "CANCELLED",
                json
                    .parseToJsonElement(cancellation.output)
                    .jsonObject
                    .getValue("status")
                    .jsonPrimitive.content,
            )
        }
    }

    private fun database() =
        DatabaseFactory.create(
            databaseUrl =
                "jdbc:h2:mem:assistant-tools-${UUID.randomUUID()};" +
                    "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        )
}
