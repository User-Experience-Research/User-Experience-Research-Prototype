package org.nmsi

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.pebbletemplates.pebble.loader.ClasspathLoader

fun Application.module() {
    install(CallLogging) {
        filter { call -> !call.request.path().startsWith("/assets/") }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled request failure", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                PebbleContent(
                    "error.peb",
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

    routing {
        staticResources("/assets", "static")

        get("/") {
            call.respondRedirect("/login")
        }
        get("/login") {
            call.respond(
                PebbleContent(
                    "login.peb",
                    mapOf("pageTitle" to "Sign in"),
                ),
            )
        }
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
    }
}
