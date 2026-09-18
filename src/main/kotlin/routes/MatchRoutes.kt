package com.example.routes


import com.example.data.models.MatchDto
import com.example.data.responses.CreateMatchResponse
import com.example.repository.MatchRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureMatchRoutes() {
    val matchRepository = MatchRepository()

    routing {
        route("/api/matches") {

            // GET /api/matches (Feed sa filterima)
           /* get {
                val sport = call.request.queryParameters["sport"]
                // Za demo pretpostavljamo userId = 1 ako nema JWT autentičnosti
                val currentUserId = 1

                val matches = matchRepository.getExploreMatches(currentUserId, sport)
                call.respond(HttpStatusCode.OK, matches)
            }*/
            get("/mine") {
                val tab = call.request.queryParameters["tab"] ?: "joined"
                val currentUserId = 1 // Privremeno dok ne izvučemo ID iz JWT tokena

                val matches = if (tab == "hosted") {
                    matchRepository.getMyHostedMatches(currentUserId)
                } else {
                    matchRepository.getMyJoinedMatches(currentUserId)
                }

                call.respond(HttpStatusCode.OK, matches)
            }
            get {
                val sport = call.request.queryParameters["sport"]
                val maxDistance = call.request.queryParameters["maxDistance"]?.toDoubleOrNull()
                val searchQuery = call.request.queryParameters["q"]

                val onlyAvailable = call.request.queryParameters["onlyAvailable"]?.toBooleanStrictOrNull() ?: true
                val currentUserId = 1 // Pretpostavljeni ulogovani korisnik

                val matches = matchRepository.getFeedMatches(
                    currentUserId = currentUserId,
                    sport = sport,
                    maxDistance = maxDistance,
                    searchQuery = searchQuery,
                    onlyAvailable=true
                )

                println(matches)
                call.respond(HttpStatusCode.OK, matches)
            }

            get("/{id}/players") {
                val matchId = call.parameters["id"]?.toIntOrNull()
                if (matchId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID"))
                    return@get
                }

                val players = matchRepository.getPlayersForMatch(matchId)
                call.respond(HttpStatusCode.OK, players)
            }
            // GET /api/matches/{id}
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID"))
                    return@get
                }

                val match = matchRepository.getMatchById(id, currentUserId = 1)
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("message" to "Meč nije pronađen"))
                }
            }
            post {
                try {
                    val dto = call.receive<MatchDto>()
                    println("Primljen mec: $dto")
                    val currentUserId = 1 // Privremeno dok ne dodamo JWT

                    val newMatchId = matchRepository.createMatch(
                        sport = dto.sport,
                        sportEmoji = dto.sportEmoji,
                        timeLeft = dto.timeLeft,
                        dateTimeStr = dto.dateTime,
                        dateLabel = dto.dateLabel,
                        timeRange = dto.timeRange,
                        venue = dto.venue,
                        address = dto.address,
                        lat = dto.lat,
                        lng = dto.lng,
                        distance = dto.distance,
                        total = dto.total,
                        pricePerPerson = dto.pricePerPerson,
                        currency = dto.currency,
                        hostId = currentUserId,
                        level = dto.level,
                        levelSrb = dto.levelSrb,
                        urgent = dto.urgent,
                        rulesList = dto.rules
                    )

                    // Korišćenje response DTO umesto mapOf znatno olakšava serijalizaciju
                    call.respond(
                        HttpStatusCode.Created,
                        CreateMatchResponse(id = newMatchId, message = "Meč uspešno kreiran")
                    )

                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to (e.message ?: "Greška pri kreiranju"))
                    )
                }
            }
            // POST /api/matches/{id}/join (Prijava na meč)
            post("/{id}/join") {
                val matchId = call.parameters["id"]?.toIntOrNull()
                if (matchId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID"))
                    return@post
                }

                val userId = 1 // Privremeno dok ne dodamo JWT extractor u request
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
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Nevalidan ID"))
                    return@delete
                }

                val userId = 1
                val success = matchRepository.leaveMatch(matchId, userId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Uspešno ste se odjavili sa meča"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Niste bili prijavljeni na ovaj meč"))
                }
            }
        }
    }
}