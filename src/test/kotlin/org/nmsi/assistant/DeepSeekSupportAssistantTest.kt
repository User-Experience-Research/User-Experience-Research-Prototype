package org.nmsi.assistant

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.nmsi.data.DatabaseFactory
import org.nmsi.data.SupportRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepSeekSupportAssistantTest {
    @Test
    fun `confirmed booking cannot be claimed until the booking tool succeeds`() {
        database().use { dataSource ->
            val repository = SupportRepository(dataSource)
            repository.ensureFutureSlots()
            val user = repository.demoUser()
            val facility = repository.searchFacilities("study skills", null).first()
            val slot = repository.availableSlots(facility.id).first()
            val responses =
                ArrayDeque(
                    listOf(
                        contentResponse("Your appointment is booked."),
                        toolResponse(
                            name = "book_appointment",
                            arguments =
                                """
                                {
                                  "facility_id": ${facility.id},
                                  "starts_at": "${slot.startsAt}",
                                  "note": "Exam preparation",
                                  "confirmed": true
                                }
                                """.trimIndent(),
                        ),
                        contentResponse("Done."),
                    ),
                )
            val assistant = assistant(repository, responses)

            val reply =
                runBlocking {
                    assistant.respond(
                        user.id,
                        "Please book ${facility.name} at ${slot.startsAt}. I explicitly confirm this booking.",
                    )
                }

            assertEquals("BOOKED", repository.listAppointments(user.id).single().status)
            assertTrue(reply.text.contains("Status: BOOKED"))
            assertTrue(reply.text.contains("Asia/Shanghai"))
            assertTrue(responses.isEmpty())
        }
    }

    @Test
    fun `confirmed cancellation must list scoped appointments and execute the cancel tool`() {
        database().use { dataSource ->
            val repository = SupportRepository(dataSource)
            repository.ensureFutureSlots()
            val user = repository.demoUser()
            val facility = repository.searchFacilities("study skills", null).first()
            val slot = repository.availableSlots(facility.id).first()
            val booked = repository.bookAppointment(user.id, facility.id, slot.startsAt, null)
            val responses =
                ArrayDeque(
                    listOf(
                        contentResponse("There is nothing to cancel."),
                        toolResponse("list_user_appointments", "{}"),
                        toolResponse(
                            name = "cancel_appointment",
                            arguments =
                                """
                                {
                                  "appointment_id": ${booked.id},
                                  "confirmed": true
                                }
                                """.trimIndent(),
                        ),
                        contentResponse("Cancelled."),
                    ),
                )
            val assistant = assistant(repository, responses)

            val reply =
                runBlocking {
                    assistant.respond(
                        user.id,
                        "Cancel my ${facility.name} appointment. I explicitly confirm this cancellation.",
                    )
                }

            assertEquals("CANCELLED", repository.appointmentById(user.id, booked.id)?.status)
            assertTrue(reply.text.contains("Status: CANCELLED"))
            assertTrue(reply.text.contains("Asia/Shanghai"))
            assertTrue(responses.isEmpty())
        }
    }

    private fun assistant(
        repository: SupportRepository,
        responses: ArrayDeque<JsonObject>,
    ) = DeepSeekSupportAssistant(
        repository = repository,
        apiKey = "test-key",
        model = "deepseek-v4-flash",
        completionOverride = { _, _, _ -> responses.removeFirst() },
    )

    private fun contentResponse(content: String): JsonObject =
        buildJsonObject {
            put(
                "choices",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put(
                                "message",
                                buildJsonObject {
                                    put("role", JsonPrimitive("assistant"))
                                    put("content", JsonPrimitive(content))
                                },
                            )
                        },
                    ),
                ),
            )
        }

    private fun toolResponse(
        name: String,
        arguments: String,
    ): JsonObject =
        buildJsonObject {
            put(
                "choices",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put(
                                "message",
                                buildJsonObject {
                                    put("role", JsonPrimitive("assistant"))
                                    put(
                                        "tool_calls",
                                        JsonArray(
                                            listOf(
                                                buildJsonObject {
                                                    put("id", JsonPrimitive("call-$name"))
                                                    put("type", JsonPrimitive("function"))
                                                    put(
                                                        "function",
                                                        buildJsonObject {
                                                            put("name", JsonPrimitive(name))
                                                            put("arguments", JsonPrimitive(arguments))
                                                        },
                                                    )
                                                },
                                            ),
                                        ),
                                    )
                                },
                            )
                        },
                    ),
                ),
            )
        }

    private fun database() =
        DatabaseFactory.create(
            databaseUrl =
                "jdbc:h2:mem:deepseek-assistant-${UUID.randomUUID()};" +
                    "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        )
}
