package com.example.routes

import com.example.data.models.MatchDto
import com.example.repository.MatchRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Extenzija za bezbedno čitanje userId iz JWT tokena
private val ApplicationCall.currentUserId: Int?
    get() {
        val principal = principal<JWTPrincipal>() ?: return null
        return principal.payload.getClaim("userId").asInt()
            ?: principal.payload.id?.toIntOrNull()
            ?: principal.payload.subject?.toIntOrNull()
    }

fun Route.configureMatchRoutes() { // Promenjeno u Route.configureMatchRoutes()
    val matchRepository = MatchRepository()

    route("/api/matches") {

        // POST /api/matches (Kreiranje novog meča)
        post {
            val dto = call.receive<MatchDto>()
            val hostId = call.currentUserId
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neautorizovan pristup"))

            val newMatchId = matchRepository.createMatch(dto, hostId)
            call.respond(HttpStatusCode.Created, mapOf("id" to newMatchId))
        }

        // GET /api/matches/mine (Moji mečevi - hosted ili joined)
        get("/mine") {
            val tab = call.request.queryParameters["tab"] ?: "joined"
            val currentUserId = call.currentUserId
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neautorizovan pristup"))

            val matches = if (tab == "hosted") {
                matchRepository.getMyHostedMatches(currentUserId)
            } else {
                matchRepository.getMyJoinedMatches(currentUserId)
            }

            call.respond(HttpStatusCode.OK, matches)
        }

        // GET /api/matches (Feed sa filterima)
        get {
            val sport = call.request.queryParameters["sport"]
            val maxDistance = call.request.queryParameters["maxDistance"]?.toDoubleOrNull()
            val searchQuery = call.request.queryParameters["q"]
            val onlyAvailable = call.request.queryParameters["onlyAvailable"]?.toBooleanStrictOrNull() ?: true

            val currentUserId = call.currentUserId
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neautorizovan pristup"))

            val matches = matchRepository.getFeedMatches(
                currentUserId = currentUserId,
                sport = sport,
                maxDistance = maxDistance,
                searchQuery = searchQuery,
                onlyAvailable = onlyAvailable
            )

            call.respond(HttpStatusCode.OK, matches)
        }

        // GET /api/matches/{id}/players (Igrači na meču)
        get("/{id}/players") {
            val matchId = call.parameters["id"]?.toIntOrNull()
            if (matchId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID meča"))
                return@get
            }

            val players = matchRepository.getPlayersForMatch(matchId)
            call.respond(HttpStatusCode.OK, players)
        }

        // GET /api/matches/{id} (Detalji meča)
        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID meča"))
                return@get
            }

            val currentUserId = call.currentUserId
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neautorizovan pristup"))

            val match = matchRepository.getMatchById(id, currentUserId = currentUserId)
            if (match != null) {
                call.respond(HttpStatusCode.OK, match)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "Meč nije pronađen"))
            }
        }

        // POST /api/matches/{id}/join (Prijava na meč)
        post("/{id}/join") {
            val matchId = call.parameters["id"]?.toIntOrNull()
            if (matchId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID meča"))
                return@post
            }

            val userId = call.currentUserId
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neautorizovan pristup"))

            val success = matchRepository.joinMatch(matchId, userId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Uspešno ste se prijavili na meč"))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Već ste prijavljeni ili je meč pun"))
            }
        }

        // DELETE /api/matches/{id}/join (Odjava sa meča)
        delete("/{id}/join") {
            val matchId = call.parameters["id"]?.toIntOrNull()
            if (matchId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID meča"))
                return@delete
            }

            val userId = call.currentUserId
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neautorizovan pristup"))

            val success = matchRepository.leaveMatch(matchId, userId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Uspešno ste se odjavili sa meča"))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Niste bili prijavljeni na ovaj meč"))
            }
        }
    }
}