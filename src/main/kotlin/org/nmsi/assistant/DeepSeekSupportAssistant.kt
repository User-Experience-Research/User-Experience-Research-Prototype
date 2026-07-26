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

class DeepSeekSupportAssistant(
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
            val messages = initialMessages(userId, message).toMutableList()
            val toolContext = ToolContext()

            repeat(MAX_TOOL_ROUNDS) {
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
                    val text = responseMessage["content"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    check(text.isNotBlank()) { "DeepSeek returned no assistant text" }
                    repository.appendConversationMessage(userId, "ASSISTANT", text)
                    return@withContext AssistantReply(
                        text = text,
                        recommendations = toolContext.facilities.values.take(3).map(::recommendation),
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
                            json.parseToJsonElement(
                                function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
                            ).jsonObject
                        }.getOrElse { JsonObject(emptyMap()) }
                    val output = executeTool(name, arguments, toolContext)
                    repository.appendConversationMessage(userId, "TOOL", "$name: $output")
                    messages.add(
                        buildJsonObject {
                            put("role", JsonPrimitive("tool"))
                            put("tool_call_id", JsonPrimitive(callId))
                            put("content", JsonPrimitive(output))
                        },
                    )
                }
            }
            error("DeepSeek exceeded the maximum number of tool rounds")
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

    private fun tools(): JsonArray =
        buildJsonArray {
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
                                    put("description", JsonPrimitive("A category slug when known, or an empty string."))
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

    private fun executeTool(
        name: String,
        arguments: JsonObject,
        context: ToolContext,
    ): String =
        when (name) {
            "search_facilities" -> {
                val query = arguments["query"]?.jsonPrimitive?.contentOrNull
                val category =
                    arguments["category_slug"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf(String::isNotBlank)
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

