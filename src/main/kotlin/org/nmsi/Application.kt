package org.nmsi

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.pebbletemplates.pebble.loader.ClasspathLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.nmsi.assistant.AssistantFactory
import org.nmsi.assistant.FacilityRecommendation
import org.nmsi.assistant.SupportAssistant
import org.nmsi.data.DatabaseFactory
import org.nmsi.data.SupportRepository
import java.time.OffsetDateTime

@Serializable
data class PortalSession(
    val userId: Long,
    val displayName: String,
)

@Serializable
data class ChatRequest(
    val message: String,
)

@Serializable
data class ChatResponse(
    val reply: String,
    val recommendations: List<FacilityRecommendation>,
    val mode: String,
)

fun Application.module() {
    val dataSource = DatabaseFactory.create()
    val repository = SupportRepository(dataSource)
    val assistant = AssistantFactory.create(repository)
    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }

    install(CallLogging) {
        filter { call -> !call.request.path().startsWith("/assets/") }
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log
                .error("Unhandled request failure", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                PebbleContent(
                    "error",
                    mapOf(
                        "pageTitle" to "Something went wrong",
                        "message" to "The service could not complete that request. Please try again.",
                    ),
                ),
            )
        }
    }
    install(Pebble) {
        loader(
            ClasspathLoader().apply {
                prefix = "templates"
                suffix = ".peb"
            },
        )
    }
    install(Sessions) {
        val signingKey =
            (System.getenv("SESSION_SECRET") ?: "nmsi-local-session-secret-change-in-production")
                .padEnd(32, '0')
                .toByteArray()
        cookie<PortalSession>("nmsi_session_v2") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            cookie.secure = System.getenv("COOKIE_SECURE")?.toBoolean() ?: false
            transform(SessionTransportTransformerMessageAuthentication(signingKey))
        }
    }

    routing {
        staticResources("/assets", "static")

        get("/") {
            call.respondRedirect(if (call.sessions.get<PortalSession>() == null) "/login" else "/dashboard")
        }
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        get("/login") {
            if (call.sessions.get<PortalSession>() != null) {
                call.respondRedirect("/dashboard")
                return@get
            }
            call.respond(
                PebbleContent(
                    "login",
                    mapOf("pageTitle" to "Sign in"),
                ),
            )
        }
        post("/login") {
            call.receiveParameters()
            val user = repository.demoUser()
            call.sessions.set(PortalSession(user.id, user.displayName))
            call.respondRedirect("/dashboard")
        }
        post("/logout") {
            call.sessions.clear<PortalSession>()
            call.respondRedirect("/login")
        }

        authenticatedPortalRoutes(repository, assistant)
    }
}

private fun Route.authenticatedPortalRoutes(
    repository: SupportRepository,
    assistant: SupportAssistant,
) {
    route("") {
        get("/dashboard") {
            val session = call.requireSession() ?: return@get
            call.respond(
                PebbleContent(
                    "dashboard",
                    portalModel(
                        session,
                        pageTitle = "Student services",
                        activePage = "dashboard",
                        "appointments" to repository.listAppointments(session.userId).filter { it.status == "BOOKED" },
                    ),
                ),
            )
        }
        get("/support") {
            val session = call.requireSession() ?: return@get
            val query = call.request.queryParameters["q"].orEmpty()
            val category = call.request.queryParameters["category"].orEmpty()
            call.respond(
                PebbleContent(
                    "support",
                    portalModel(
                        session,
                        pageTitle = "Student Support Navigator",
                        activePage = "support",
                        "query" to query,
                        "selectedCategory" to category,
                        "categories" to repository.listCategories(),
                        "facilities" to repository.searchFacilities(query, category),
                    ),
                ),
            )
        }
        get("/support/{id}") {
            val session = call.requireSession() ?: return@get
            val facilityId = call.parameters["id"]?.toLongOrNull()
            val facility = facilityId?.let(repository::facilityById)
            if (facility == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    PebbleContent(
                        "error",
                        mapOf(
                            "pageTitle" to "Support source not found",
                            "message" to "That support source is not in the NMSI directory.",
                        ),
                    ),
                )
                return@get
            }
            call.respond(
                PebbleContent(
                    "facility",
                    portalModel(
                        session,
                        pageTitle = facility.name,
                        activePage = "support",
                        "facility" to facility,
                        "availableSlots" to repository.availableSlots(facility.id),
                    ),
                ),
            )
        }
        get("/appointments") {
            val session = call.requireSession() ?: return@get
            call.respond(
                PebbleContent(
                    "appointments",
                    portalModel(
                        session,
                        pageTitle = "My appointments",
                        activePage = "appointments",
                        "appointments" to repository.listAppointments(session.userId),
                        "booked" to (call.request.queryParameters["booked"] == "1"),
                        "cancelled" to (call.request.queryParameters["cancelled"] == "1"),
                    ),
                ),
            )
        }
        post("/appointments") {
            val session = call.requireSession() ?: return@post
            val parameters = call.receiveParameters()
            val facilityId = parameters["facilityId"]?.toLongOrNull()
            val startsAt = parameters["startsAt"]?.let(OffsetDateTime::parse)
            if (facilityId == null || startsAt == null || repository.facilityById(facilityId) == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid appointment request")
                return@post
            }
            repository.bookAppointment(
                userId = session.userId,
                facilityId = facilityId,
                startsAt = startsAt,
                note = parameters["note"],
            )
            call.respondRedirect("/appointments?booked=1")
        }
        post("/appointments/{id}/cancel") {
            val session = call.requireSession() ?: return@post
            val appointmentId = call.parameters["id"]?.toLongOrNull()
            if (appointmentId != null) {
                repository.cancelAppointment(session.userId, appointmentId)
            }
            call.respondRedirect("/appointments?cancelled=1")
        }
        post("/api/chat") {
            val session = call.requireSession() ?: return@post
            val request = call.receive<ChatRequest>()
            val message = request.message.trim().take(1200)
            if (message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message is required"))
                return@post
            }
            val reply = assistant.respond(session.userId, message)
            call.respond(
                ChatResponse(
                    reply = reply.text,
                    recommendations = reply.recommendations,
                    mode = reply.mode,
                ),
            )
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireSession(): PortalSession? {
    val session = sessions.get<PortalSession>()
    if (session == null) {
        respondRedirect("/login")
    }
    return session
}

private fun portalModel(
    session: PortalSession,
    pageTitle: String,
    activePage: String,
    vararg values: Pair<String, Any?>,
): Map<String, Any> =
    buildMap<String, Any> {
        put("pageTitle", pageTitle)
        put("activePage", activePage)
        put("session", session)
        values.forEach { (key, value) ->
            if (value != null) put(key, value)
        }
    }
