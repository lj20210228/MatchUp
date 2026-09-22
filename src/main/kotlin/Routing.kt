package com.example

import com.example.db.PushSubscriptionsTable
import com.example.db.dbQuery
import com.example.routes.configureAuthRoutes
import com.example.routes.configureChatRoutes
import com.example.routes.configureMatchRoutes
import com.example.routes.extractUserIdFromCall
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insertIgnore

@kotlinx.serialization.Serializable
data class PushSubscriptionDto(
    val endpoint: String,
    val keys: KeysDto
)

@Serializable
data class KeysDto(
    val p256dh: String,
    val auth: String
)
fun Application.configureRouting() {
    routing {
        // Login i registracija ostaju javni.
        configureAuthRoutes()

        // Sve unutar ovog bloka zahteva validan Bearer JWT.
        authenticate("auth-jwt") {
            configureMatchRoutes()
            configureChatRoutes()
            post("/api/notifications/subscribe") {
                val userId = extractUserIdFromCall(call)?: return@post call.respond(HttpStatusCode.Unauthorized)
                val dto = call.receive<PushSubscriptionDto>()

                dbQuery {
                    PushSubscriptionsTable.insertIgnore {
                        it[PushSubscriptionsTable.userId] = userId
                        it[endpoint] = dto.endpoint
                        it[p256dh] = dto.keys.p256dh
                        it[auth] = dto.keys.auth
                    }
                }

                call.respond(HttpStatusCode.OK)
            }


            get("/") {
                call.respondText("Hello, World!")
            }

            get("/json/kotlinx-serialization") {
                call.respond(mapOf("hello" to "world"))
            }


        }

    }
}