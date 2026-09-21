package com.example.routes

import com.auth0.jwt.JWT
import com.example.repository.ChatRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

private val chatSessions =
    ConcurrentHashMap<Int, MutableSet<WebSocketSession>>()

fun Route.configureChatRoutes() {
    val chatRepository = ChatRepository()

    route("/api/chats") {
        get {
            val userId = call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim("userId")
                ?.asInt()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val chats = chatRepository.getUserChats(userId)

            call.respond(HttpStatusCode.OK, chats)
        }
        get("/{matchId}/messages") {
            val matchId = call.parameters["matchId"]?.toIntOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Nevalidan ID meča")
                )

            val currentUserId = call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim("userId")
                ?.asInt()
                ?: call.principal<JWTPrincipal>()
                    ?.payload
                    ?.subject
                    ?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val matchName =
                call.request.queryParameters["matchName"] ?: "Grupni čet"

            try {
                val messages = chatRepository.getChatHistory(
                    matchId = matchId,
                    matchName = matchName,
                    currentUserId = currentUserId
                )

                call.respond(HttpStatusCode.OK, messages)
            } catch (error: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to (error.message ?: "Greška pri učitavanju poruka"))
                )
            }
        }

        webSocket("/{matchId}") {
            val matchId = call.parameters["matchId"]?.toIntOrNull()

            if (matchId == null) {
                close(
                    CloseReason(
                        CloseReason.Codes.VIOLATED_POLICY,
                        "Invalid match ID"
                    )
                )
                return@webSocket
            }

            val token = call.request.queryParameters["token"]
            val userId = extractUserIdFromToken(token)

            if (userId == null) {
                close(
                    CloseReason(
                        CloseReason.Codes.VIOLATED_POLICY,
                        "Unauthorized"
                    )
                )
                return@webSocket
            }

            val matchName =
                call.request.queryParameters["matchName"] ?: "Grupni čet"

            val sessions = chatSessions.computeIfAbsent(matchId) {
                ConcurrentHashMap.newKeySet()
            }

            sessions.add(this)

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue

                    val rawText = frame.readText().trim()

                    if (rawText.isBlank()) continue

                    val messageResponse = chatRepository.processAndSaveMessage(
                        matchId = matchId,
                        matchName = matchName,
                        senderId = userId,
                        text = rawText
                    )

                    val jsonMessage = Json.encodeToString(messageResponse)

                    for (session in sessions) {
                        try {
                            session.send(Frame.Text(jsonMessage))
                        } catch (_: Exception) {
                            sessions.remove(session)
                        }
                    }
                }
            } catch (error: Exception) {
                println("WebSocket chat error: ${error.message}")
            } finally {
                sessions.remove(this)

                if (sessions.isEmpty()) {
                    chatSessions.remove(matchId)
                }
            }
        }
    }
}

private fun extractUserIdFromToken(token: String?): Int? {
    if (token.isNullOrBlank()) return null

    return try {
        val decoded = JWT.decode(token)

        decoded.getClaim("userId").asInt()
            ?: decoded.getClaim("id").asInt()
            ?: decoded.subject?.toIntOrNull()
    } catch (error: Exception) {
        println("JWT parsing error: ${error.message}")
        null
    }
}