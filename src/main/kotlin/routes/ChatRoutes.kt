package com.example.routes

import com.auth0.jwt.JWT
import com.example.repository.ChatRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

private val chatSessions = ConcurrentHashMap<Int, MutableSet<WebSocketSession>>()

fun Route.configureChatRoutes() {
    val chatRepository = ChatRepository()

    route("/api/chats") {

        // 1. Dohvatanje svih čatova za trenutnog korisnika
        get {
            val userId = extractUserIdFromCall(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val chats = chatRepository.getUserChats(userId)
            call.respond(HttpStatusCode.OK, chats)
        }

        // 2. Dohvatanje istorije poruka po CHAT ID-u
        get("/{chatId}/messages") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Nevalidan ID čata")
                )

            val currentUserId = extractUserIdFromCall(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            try {
                val messages = chatRepository.getChatHistory(
                    chatId = chatId,
                    currentUserId = currentUserId,

                )
                call.respond(HttpStatusCode.OK, messages)
            } catch (error: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to (error.message ?: "Greška pri učitavanju poruka"))
                )
            }
        }

        // 3. Slanje poruke putem HTTP REST-a
        post("/{chatId}/messages") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Nevalidan ID čata")
                )

            val userId = extractUserIdFromCall(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val requestBody = call.receiveNullable<Map<String, String>>()
            val text = requestBody?.get("text")?.trim()

            if (text.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Poruka ne može biti prazna")
                )
            }

            try {
                val messageResponse = chatRepository.processAndSaveMessage(
                    chatId = chatId,
                    senderId = userId,
                    text = text
                )

                // Emitovanje poruke svim povezanim WebSocket klijentima na tom chatu
                chatSessions[chatId]?.forEach { session ->
                    try {
                        session.send(Frame.Text(Json.encodeToString(messageResponse)))
                    } catch (_: Exception) { }
                }

                call.respond(HttpStatusCode.Created, messageResponse)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to (e.message ?: "Greška pri slanju poruke"))
                )
            }
        }

        // 4. Označavanje čata kao pročitanog
        post("/{chatId}/read") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Nevalidan ID čata")
                )

            val userId = extractUserIdFromCall(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            try {
                chatRepository.markAsRead(chatId = chatId, userId = userId)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to (e.message ?: "Greška pri ažuriranju statusa"))
                )
            }
        }

        // 5. WebSocket veza (prebačena na /ws/{chatId} da ne kolidira sa REST GET-om)
        webSocket("/ws/{chatId}") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()

            if (chatId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Nevalidan Chat ID"))
                return@webSocket
            }

            val token = call.request.queryParameters["token"]
            val userId = extractUserIdFromToken(token)

            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Neautorizovan pristup"))
                return@webSocket
            }

            val sessions = chatSessions.computeIfAbsent(chatId) {
                ConcurrentHashMap.newKeySet()
            }
            sessions.add(this)

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val rawText = frame.readText().trim()
                    if (rawText.isBlank()) continue

                    val messageResponse = chatRepository.processAndSaveMessage(
                        chatId = chatId,
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
                println("WebSocket error: ${error.message}")
            } finally {
                sessions.remove(this)
                if (sessions.isEmpty()) {
                    chatSessions.remove(chatId)
                }
            }
        }
    }
}

fun extractUserIdFromCall(call: ApplicationCall): Int? {
    val principal = call.principal<JWTPrincipal>() ?: return null
    return principal.payload.getClaim("userId").asInt()
        ?: principal.payload.getClaim("id").asInt()
        ?: principal.payload.subject?.toIntOrNull()
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