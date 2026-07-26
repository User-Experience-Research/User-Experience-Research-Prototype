package org.nmsi.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.nmsi.data.Appointment
import org.nmsi.data.Facility
import org.nmsi.data.SupportRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DeepSeekSupportAssistant(
    private val repository: SupportRepository,
    private val apiKey: String,
    private val model: String,
    private val completionOverride: ((Long, List<JsonObject>, JsonArray) -> JsonObject)? = null,
) : SupportAssistant {
    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val toolExecutor = AssistantToolExecutor(repository, json)
    private val systemPrompt by lazy(::loadSystemPrompt)

    override suspend fun respond(
        userId: Long,
        message: String,
    ): AssistantReply =
        withContext(Dispatchers.IO) {
            repository.appendConversationMessage(userId, "USER", message)
            val messages = initialMessages(userId, message).toMutableList()
            val toolContext = ToolContext()

            repeat(MAX_TOOL_ROUNDS) toolLoop@{
                val response = postChatCompletion(userId, messages)
                val responseMessage =
                    response
                        .getValue("choices")
                        .jsonArray
                        .first()
                        .jsonObject
                        .getValue("message")
                        .jsonObject
                val toolCalls = responseMessage["tool_calls"]?.jsonArray.orEmpty()

                if (toolCalls.isEmpty()) {
                    val text =
                        responseMessage["content"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty()
                            .trim()
                    check(text.isNotBlank()) { "DeepSeek returned no assistant text" }
                    val correction = requiredToolCorrection(message, toolContext)
                    if (correction != null) {
                        messages.add(responseMessage)
                        messages.add(
                            buildJsonObject {
                                put("role", JsonPrimitive("system"))
                                put("content", JsonPrimitive(correction))
                            },
                        )
                        return@toolLoop
                    }
                    val groundedText = groundedFinalText(message, text, toolContext)
                    repository.appendConversationMessage(userId, "ASSISTANT", groundedText)
                    return@withContext AssistantReply(
                        text = groundedText,
                        recommendations =
                            toolContext.facilities.values
                                .take(3)
                                .map(::recommendation),
                        mode = "DeepSeek V4 Flash",
                    )
                }

                messages.add(responseMessage)
                toolCalls.forEach { item ->
                    val call = item.jsonObject
                    val callId = call.getValue("id").jsonPrimitive.content
                    val function = call.getValue("function").jsonObject
                    val name = function.getValue("name").jsonPrimitive.content
                    val arguments =
                        runCatching {
                            json
                                .parseToJsonElement(
                                    function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
                                ).jsonObject
                        }.getOrElse { JsonObject(emptyMap()) }
                    val execution = toolExecutor.execute(userId, name, arguments)
                    execution.facilities.forEach { facility -> toolContext.facilities[facility.id] = facility }
                    toolContext.executedTools.add(name)
                    execution.appointments?.let { appointments -> toolContext.appointments = appointments }
                    execution.mutation?.let { mutation -> toolContext.mutation = mutation }
                    repository.appendConversationMessage(userId, "TOOL", "$name: ${execution.output}")
                    messages.add(
                        buildJsonObject {
                            put("role", JsonPrimitive("tool"))
                            put("tool_call_id", JsonPrimitive(callId))
                            put("content", JsonPrimitive(execution.output))
                        },
                    )
                }
            }
            val safeText =
                "I could not complete that database request safely. No booking or cancellation has been claimed. " +
                    "Please try again, or use My appointments to review the current record."
            repository.appendConversationMessage(userId, "ASSISTANT", safeText)
            AssistantReply(
                text = safeText,
                recommendations =
                    toolContext.facilities.values
                        .take(3)
                        .map(::recommendation),
                mode = "DeepSeek V4 Flash",
            )
        }

    private fun initialMessages(
        userId: Long,
        message: String,
    ): List<JsonObject> =
        buildList {
            add(
                buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(systemPrompt))
                },
            )
            repository.recentConversation(userId, 10).dropLast(1).forEach { previous ->
                if (previous.role != "TOOL") {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive(if (previous.role == "USER") "user" else "assistant"))
                            put("content", JsonPrimitive(previous.content))
                        },
                    )
                }
            }
            add(
                buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(message))
                },
            )
        }

    private fun postChatCompletion(
        userId: Long,
        messages: List<JsonObject>,
    ): JsonObject {
        completionOverride?.let { completion -> return completion(userId, messages, tools()) }
        val body =
            buildJsonObject {
                put("model", JsonPrimitive(model))
                put("messages", JsonArray(messages))
                put("tools", tools())
                put("tool_choice", JsonPrimitive("auto"))
                put("thinking", buildJsonObject { put("type", JsonPrimitive("disabled")) })
                put("max_tokens", JsonPrimitive(900))
                put("stream", JsonPrimitive(false))
                put("user_id", JsonPrimitive(safetyIdentifier(userId)))
            }
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI("https://api.deepseek.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() in 200..299) {
            "DeepSeek request failed with status ${response.statusCode()}"
        }
        return json.parseToJsonElement(response.body()).jsonObject
    }

    private fun tools(): JsonArray {
        val categorySlugs = listOf("") + repository.listCategories().map { category -> category.slug }
        return buildJsonArray {
            add(
                functionTool(
                    name = "list_categories",
                    description =
                        "List the complete current NMSI support taxonomy. Use this when a category is uncertain; never invent a category slug.",
                    properties = buildJsonObject {},
                    required = emptyList(),
                ),
            )
            add(
                functionTool(
                    name = "search_facilities",
                    description =
                        "Search the NMSI database for real support facilities using the student's own words and an optional category. Returns verified scope, provider, access, response time, distance and rating.",
                    properties =
                        buildJsonObject {
                            put(
                                "query",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("A short search phrase in the student's own words."))
                                },
                            )
                            put(
                                "category_slug",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put(
                                        "description",
                                        JsonPrimitive(
                                            "One exact slug from list_categories, or an empty string when uncertain.",
                                        ),
                                    )
                                    put("enum", JsonArray(categorySlugs.map(::JsonPrimitive)))
                                },
                            )
                        },
                    required = listOf("query", "category_slug"),
                ),
            )
            add(
                functionTool(
                    name = "get_facility",
                    description = "Read the complete verified details for one NMSI support facility.",
                    properties =
                        buildJsonObject {
                            put(
                                "facility_id",
                                buildJsonObject {
                                    put("type", JsonPrimitive("integer"))
                                    put("description", JsonPrimitive("Facility id returned by search_facilities."))
                                },
                            )
                        },
                    required = listOf("facility_id"),
                ),
            )
            add(
                functionTool(
                    name = "list_user_appointments",
                    description =
                        "List appointments belonging to the signed-in student. The server scopes this tool, so it accepts no user id.",
                    properties = buildJsonObject {},
                    required = emptyList(),
                ),
            )
            add(
                functionTool(
                    name = "get_available_slots",
                    description = "List verified available appointment times for one facility.",
                    properties =
                        buildJsonObject {
                            put(
                                "facility_id",
                                buildJsonObject {
                                    put("type", JsonPrimitive("integer"))
                                    put("description", JsonPrimitive("Verified facility id."))
                                },
                            )
                        },
                    required = listOf("facility_id"),
                ),
            )
            add(
                functionTool(
                    name = "book_appointment",
                    description =
                        "Book an available slot for the signed-in student. Call only after the student explicitly confirms the facility and exact time.",
                    properties =
                        buildJsonObject {
                            put(
                                "facility_id",
                                buildJsonObject {
                                    put("type", JsonPrimitive("integer"))
                                    put("description", JsonPrimitive("Verified facility id."))
                                },
                            )
                            put(
                                "starts_at",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("Exact ISO-8601 offset time returned by get_available_slots."))
                                },
                            )
                            put(
                                "note",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("Optional note from the student, or an empty string."))
                                },
                            )
                            put(
                                "confirmed",
                                buildJsonObject {
                                    put("type", JsonPrimitive("boolean"))
                                    put(
                                        "description",
                                        JsonPrimitive(
                                            "True only after the student explicitly confirmed this facility and exact time.",
                                        ),
                                    )
                                },
                            )
                        },
                    required = listOf("facility_id", "starts_at", "note", "confirmed"),
                ),
            )
            add(
                functionTool(
                    name = "cancel_appointment",
                    description =
                        "Cancel one booked appointment belonging to the signed-in student. Call only after explicit confirmation of the appointment.",
                    properties =
                        buildJsonObject {
                            put(
                                "appointment_id",
                                buildJsonObject {
                                    put("type", JsonPrimitive("integer"))
                                    put("description", JsonPrimitive("Appointment id returned by list_user_appointments."))
                                },
                            )
                            put(
                                "confirmed",
                                buildJsonObject {
                                    put("type", JsonPrimitive("boolean"))
                                    put(
                                        "description",
                                        JsonPrimitive(
                                            "True only after the student explicitly confirmed cancellation of this appointment.",
                                        ),
                                    )
                                },
                            )
                        },
                    required = listOf("appointment_id", "confirmed"),
                ),
            )
        }
    }

    private fun functionTool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String>,
    ): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("function"))
            put(
                "function",
                buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("description", JsonPrimitive(description))
                    put(
                        "parameters",
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", properties)
                            put("required", JsonArray(required.map(::JsonPrimitive)))
                            put("additionalProperties", JsonPrimitive(false))
                        },
                    )
                },
            )
        }

    private fun requiredToolCorrection(
        message: String,
        context: ToolContext,
    ): String? {
        val intent = actionIntent(message)
        return when {
            (intent.wantsAppointments || intent.cancel) &&
                "list_user_appointments" !in context.executedTools ->
                "You must call list_user_appointments before answering this request. Use only that result."
            intent.book && intent.confirmed && "book_appointment" !in context.executedTools ->
                "The student explicitly confirmed a booking, but no booking tool was executed. " +
                    "Use the database tools to verify the facility and current slot, then call book_appointment. " +
                    "Do not claim success without a successful tool result."
            intent.cancel && intent.confirmed && "cancel_appointment" !in context.executedTools ->
                "The student explicitly confirmed cancellation, but no cancellation tool was executed. " +
                    "Call cancel_appointment for the verified booked appointment. " +
                    "Do not claim success without a successful tool result."
            else -> null
        }
    }

    private fun groundedFinalText(
        message: String,
        modelText: String,
        context: ToolContext,
    ): String {
        val intent = actionIntent(message)
        val mutation = context.mutation
        return when {
            mutation != null -> mutationReply(mutation)
            intent.cancel && !intent.confirmed && context.appointments != null ->
                cancellationConfirmation(context.appointments.orEmpty())
            intent.wantsAppointments && context.appointments != null ->
                appointmentSummary(context.appointments.orEmpty(), intent.bookedOnly)
            else -> modelText
        }
    }

    private fun mutationReply(mutation: AssistantMutation): String {
        val appointment = mutation.appointment
        val localTime = appointment.startsAt.atZoneSameInstant(INSTITUTE_ZONE).format(INSTITUTE_TIME_FORMAT)
        return when (mutation.type) {
            AssistantMutationType.BOOKED ->
                "Your appointment with ${appointment.facilityName} is booked for $localTime " +
                    "(${INSTITUTE_ZONE.id}). Status: BOOKED. You can review it in My appointments."
            AssistantMutationType.CANCELLED ->
                "Your appointment with ${appointment.facilityName} at $localTime " +
                    "(${INSTITUTE_ZONE.id}) has been cancelled. Status: CANCELLED. " +
                    "You can review the updated record in My appointments."
        }
    }

    private fun cancellationConfirmation(appointments: List<Appointment>): String {
        val booked = appointments.filter { appointment -> appointment.status == "BOOKED" }
        return when {
            booked.isEmpty() -> "You do not currently have a booked appointment to cancel."
            booked.size == 1 -> {
                val appointment = booked.single()
                val localTime =
                    appointment.startsAt
                        .atZoneSameInstant(INSTITUTE_ZONE)
                        .format(INSTITUTE_TIME_FORMAT)
                "I found your booked appointment with ${appointment.facilityName} at $localTime " +
                    "(${INSTITUTE_ZONE.id}). Would you like me to cancel this appointment? Please confirm."
            }
            else ->
                "I found more than one booked appointment. Please tell me which facility and time you want to cancel: " +
                    booked.joinToString("; ") { appointment -> appointment.summaryLabel() }
        }
    }

    private fun appointmentSummary(
        appointments: List<Appointment>,
        bookedOnly: Boolean,
    ): String {
        val relevant =
            if (bookedOnly) {
                appointments.filter { appointment -> appointment.status == "BOOKED" }
            } else {
                appointments
            }
        return if (relevant.isEmpty()) {
            if (bookedOnly) {
                "You do not currently have any booked appointments."
            } else {
                "You do not have any appointment records."
            }
        } else {
            "Your appointment record: " +
                relevant.joinToString("; ") { appointment ->
                    "${appointment.summaryLabel()} — ${appointment.status}"
                } + "."
        }
    }

    private fun Appointment.summaryLabel(): String {
        val localTime = startsAt.atZoneSameInstant(INSTITUTE_ZONE).format(INSTITUTE_TIME_FORMAT)
        return "$facilityName at $localTime (${INSTITUTE_ZONE.id})"
    }

    private fun actionIntent(message: String): ActionIntent {
        val normalized = message.lowercase()
        val mentionsAppointment =
            APPOINTMENT_WORDS.any(normalized::contains)
        return ActionIntent(
            book = BOOK_WORDS.any(normalized::contains),
            cancel = CANCEL_WORDS.any(normalized::contains),
            confirmed = CONFIRMATION_WORDS.any(normalized::contains),
            wantsAppointments =
                mentionsAppointment &&
                    APPOINTMENT_RECORD_WORDS.any(normalized::contains),
            bookedOnly =
                BOOKED_ONLY_WORDS.any(normalized::contains),
        )
    }

    private fun recommendation(facility: Facility) =
        FacilityRecommendation(
            id = facility.id,
            name = facility.name,
            reason = "${facility.responseTime}; ${facility.contactMode}.",
            detailUrl = "/support/${facility.id}",
        )

    private fun safetyIdentifier(userId: Long): String {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest("nmsi-support-user:$userId".toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun loadSystemPrompt(): String =
        checkNotNull(javaClass.getResource("/prompts/support-assistant-system.txt")) {
            "Support assistant system prompt is missing"
        }.readText()

    private data class ToolContext(
        val facilities: LinkedHashMap<Long, Facility> = linkedMapOf(),
        val executedTools: MutableSet<String> = linkedSetOf(),
        var appointments: List<Appointment>? = null,
        var mutation: AssistantMutation? = null,
    )

    private data class ActionIntent(
        val book: Boolean,
        val cancel: Boolean,
        val confirmed: Boolean,
        val wantsAppointments: Boolean,
        val bookedOnly: Boolean,
    )

    private companion object {
        const val MAX_TOOL_ROUNDS = 6
        val INSTITUTE_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val INSTITUTE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")
        val APPOINTMENT_WORDS = setOf("appointment", "booking", "booked", "schedule")
        val BOOK_WORDS = setOf("book ", "reserve ", "schedule ")
        val CANCEL_WORDS = setOf("cancel", "remove appointment")
        val CONFIRMATION_WORDS = setOf("confirm", "yes", "go ahead", "do it")
        val APPOINTMENT_RECORD_WORDS =
            setOf(
                "my appointment",
                "my booking",
                "my booked",
                "booked appointment",
                "current appointment",
                "upcoming appointment",
                "appointment record",
                "booking record",
                "do i have",
                "appointments do i have",
            )
        val BOOKED_ONLY_WORDS = setOf("booked", "active", "upcoming")
    }
}
