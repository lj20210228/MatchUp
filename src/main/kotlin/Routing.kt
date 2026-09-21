package com.example

import com.example.routes.configureAuthRoutes
import com.example.routes.configureChatRoutes
import com.example.routes.configureMatchRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText

fun Application.configureRouting() {
    routing {
        // Login i registracija ostaju javni.
        configureAuthRoutes()

        // Sve unutar ovog bloka zahteva validan Bearer JWT.
        authenticate("auth-jwt") {
            configureMatchRoutes()
            configureChatRoutes()


            get("/") {
                call.respondText("Hello, World!")
            }

            get("/json/kotlinx-serialization") {
                call.respond(mapOf("hello" to "world"))
            }


        }

    }
}