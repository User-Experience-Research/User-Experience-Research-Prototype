package org.nmsi.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.nmsi.data.Facility
import org.nmsi.data.SupportRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

class OpenAiSupportAssistant(
    private val repository: SupportRepository,
    private val apiKey: String,
    private val model: String,
) : SupportAssistant {
    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val systemPrompt by lazy(::loadSystemPrompt)

    override suspend fun respond(
        userId: Long,
        message: String,
    ): AssistantReply =
        withContext(Dispatchers.IO) {
            repository.appendConversationMessage(userId, "USER", message)
            val toolContext = ToolContext()
            var requestBody = initialRequest(userId, message)
            repeat(MAX_TOOL_ROUNDS) {
                val response = postResponse(requestBody)
                val functionCalls =
                    response["output"]
                        ?.jsonArray
                        ?.filter { item -> item.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "function_call" }
                        .orEmpty()
                if (functionCalls.isEmpty()) {
                    val text = extractOutputText(response)
                    check(text.isNotBlank()) { "OpenAI returned no assistant text" }
                    repository.appendConversationMessage(userId, "ASSISTANT", text)
                    return@withContext AssistantReply(
                        text = text,
                        recommendations = toolContext.facilities.values.take(3).map(::recommendation),
                        mode = "OpenAI Responses API",
                    )
                }

                val outputs =
                    buildJsonArray {
                        functionCalls.forEach { item ->
                            val call = item.jsonObject
                            val callId = call.getValue("call_id").jsonPrimitive.content
                            val name = call.getValue("name").jsonPrimitive.content
                            val arguments =
                                json.parseToJsonElement(
                                    call["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
                                ).jsonObject
                            val toolOutput = executeTool(name, arguments, toolContext)
                            repository.appendConversationMessage(userId, "TOOL", "$name: $toolOutput")
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("function_call_output"))
                                    put("call_id", JsonPrimitive(callId))
                                    put("output", JsonPrimitive(toolOutput))
                                },
                            )
                        }
                    }
                requestBody =
                    continuationRequest(
                        userId = userId,
                        previousResponseId = response.getValue("id").jsonPrimitive.content,
                        toolOutputs = outputs,
                    )
            }
            error("OpenAI exceeded the maximum number of tool rounds")
        }

    private fun initialRequest(
        userId: Long,
        message: String,
    ): JsonObject {
        val history = repository.recentConversation(userId, 10).dropLast(1)
        return commonRequest(userId) {
            put(
                "input",
                buildJsonArray {
                    history.forEach { previous ->
                        if (previous.role != "TOOL") {
                            add(
                                buildJsonObject {
                                    put(
                                        "role",
                                        JsonPrimitive(if (previous.role == "USER") "user" else "assistant"),
                                    )
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
                },
            )
        }
    }

    private fun continuationRequest(
        userId: Long,
        previousResponseId: String,
        toolOutputs: JsonArray,
    ): JsonObject =
        commonRequest(userId) {
            put("previous_response_id", JsonPrimitive(previousResponseId))
            put("input", toolOutputs)
        }

    private fun commonRequest(
        userId: Long,
        additionalFields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject =
        buildJsonObject {
            put("model", JsonPrimitive(model))
            put("instructions", JsonPrimitive(systemPrompt))
            put("safety_identifier", JsonPrimitive(safetyIdentifier(userId)))
            put("reasoning", buildJsonObject { put("effort", JsonPrimitive("low")) })
            put("text", buildJsonObject { put("verbosity", JsonPrimitive("low")) })
            put("tools", tools())
            additionalFields()
        }

    private fun tools(): JsonArray =
        buildJsonArray {
            add(
                functionTool(
                    name = "search_facilities",
                    description =
                        "Search the NMSI database for real support facilities by a student's own words and optional category slug. Returns scope, provider, access, response time, distance, rating and facility id.",
                    properties =
                        buildJsonObject {
                            put(
                                "query",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("Short search phrase in the student's own words."))
                                },
                            )
                            put(
                                "category_slug",
                                buildJsonObject {
                                    put("type", buildJsonArray { add(JsonPrimitive("string")); add(JsonPrimitive("null")) })
                                    put("description", JsonPrimitive("Optional known category slug, otherwise null."))
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
        }

    private fun functionTool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String>,
    ): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("function"))
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put("strict", JsonPrimitive(true))
            put(
                "parameters",
                buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", properties)
                    put("required", JsonArray(required.map(::JsonPrimitive)))
                    put("additionalProperties", JsonPrimitive(false))
                },
            )
        }

    private fun executeTool(
        name: String,
        arguments: JsonObject,
        context: ToolContext,
    ): String =
        when (name) {
            "search_facilities" -> {
                val query = arguments["query"]?.jsonPrimitive?.contentOrNull
                val category = arguments["category_slug"]?.jsonPrimitive?.contentOrNull
                val facilities = repository.searchFacilities(query, category).take(6)
                facilities.forEach { context.facilities[it.id] = it }
                json.encodeToString(facilities.map(::facilityPayload))
            }
            "get_facility" -> {
                val id = arguments["facility_id"]?.jsonPrimitive?.content?.toLongOrNull()
                val facility = id?.let(repository::facilityById)
                if (facility == null) {
                    """{"error":"Facility not found"}"""
                } else {
                    context.facilities[facility.id] = facility
                    json.encodeToString(facilityPayload(facility))
                }
            }
            else -> """{"error":"Unsupported tool"}"""
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
            "categories" to JsonArray(facility.categories.map { JsonPrimitive(it.name) }),
        )

    private fun postResponse(body: JsonObject): JsonObject {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() in 200..299) {
            "OpenAI request failed with status ${response.statusCode()}"
        }
        return json.parseToJsonElement(response.body()).jsonObject
    }

    private fun extractOutputText(response: JsonObject): String =
        response["output"]
            ?.jsonArray
            ?.asSequence()
            ?.map(JsonElement::jsonObject)
            ?.filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
            ?.flatMap { it["content"]?.jsonArray?.asSequence().orEmpty() }
            ?.map(JsonElement::jsonObject)
            ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()

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
    )

    private companion object {
        const val MAX_TOOL_ROUNDS = 4
    }
}

