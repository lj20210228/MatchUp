package com.example.service

import com.example.db.MatchPlayersTable
import com.example.db.MatchesTable
import com.example.db.UsersTable
import com.example.db.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import java.time.LocalDateTime

class MatchService {

    // Dohvatanje svih mečeva
    suspend fun getAllMatches(): List<ResultRow> = dbQuery {
        MatchesTable.selectAll().toList()
    }

    // Dohvatanje mečeva sa filterima na nivou SQL baze (prošli/budući, sport, radijus, pretraga i dostupnost mesta)
    suspend fun getMatchesFiltered(
        sport: String?,
        maxDistance: Double?,
        searchQuery: String?,
        onlyAvailable: Boolean = false
    ): List<ResultRow> = dbQuery {
        var query = MatchesTable.selectAll()
            .where { MatchesTable.dateTime greaterEq LocalDateTime.now() }

        // Filter za dostupna mesta: prikazuje samo mečeve gde je popunjenost manja od ukupnog broja
        if (onlyAvailable) {
            query = query.andWhere { MatchesTable.joined less MatchesTable.total }
        }

        if (!sport.isNullOrBlank() && !sport.equals("All", ignoreCase = true)) {
            query = query.andWhere { MatchesTable.sport eq sport }
        }

        if (maxDistance != null) {
            query = query.andWhere { MatchesTable.distance lessEq maxDistance }
        }

        if (!searchQuery.isNullOrBlank()) {
            val pattern = "%${searchQuery.lowercase()}%"
            query = query.andWhere { MatchesTable.venue.lowerCase() like pattern }
        }

        query.orderBy(MatchesTable.dateTime to SortOrder.ASC).toList()
    }

    // Dohvatanje meča po ID-u
    suspend fun getMatchById(id: Int): ResultRow? = dbQuery {
        MatchesTable.selectAll().where { MatchesTable.id eq id }.singleOrNull()
    }

    // Dohvatanje korisnika po ID-u (za hosta)
    suspend fun getHostUser(hostId: Int): ResultRow? = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq hostId }.singleOrNull()
    }

    // Provera da li je korisnik prijavljen na meč
    suspend fun isUserJoined(matchId: Int, userId: Int): Boolean = dbQuery {
        MatchPlayersTable.selectAll()
            .where { (MatchPlayersTable.matchId eq matchId) and (MatchPlayersTable.userId eq userId) }
            .count() > 0
    }

    // Dodavanje igrača u match_players tabelu
    suspend fun insertMatchPlayer(matchId: Int, userId: Int, isHost: Boolean): Boolean = dbQuery {
        MatchPlayersTable.insertIgnore {
            it[MatchPlayersTable.matchId] = matchId
            it[MatchPlayersTable.userId] = userId
            it[MatchPlayersTable.isHost] = isHost
        }.insertedCount > 0
    }

    // Uklanjanje igrača iz match_players tabele
    suspend fun deleteMatchPlayer(matchId: Int, userId: Int): Boolean = dbQuery {
        MatchPlayersTable.deleteWhere {
            (MatchPlayersTable.matchId eq matchId) and (MatchPlayersTable.userId eq userId)
        } > 0
    }

    // Ažuriranje broja prijavljenih igrača u tabeli matches
    suspend fun updateJoinedCount(matchId: Int, delta: Int): Int = dbQuery {
        MatchesTable.update({ MatchesTable.id eq matchId }) {
            with(SqlExpressionBuilder) {
                it.update(joined, joined + delta)
            }
        }
    }

    // Insert novog meča
    suspend fun insertMatch(
        sport: String,
        sportEmoji: String,
        timeLeft: String,
        dateTimeStr: String,
        dateLabel: String,
        timeRange: String,
        venue: String,
        address: String,
        lat: Double,
        lng: Double,
        distance: Double,
        total: Int,
        pricePerPerson: Int,
        currency: String,
        hostId: Int,
        level: String,
        levelSrb: String,
        urgent: Boolean,
        rulesJoined: List<String>
    ): Int = dbQuery {
        MatchesTable.insert {
            it[MatchesTable.sport] = sport
            it[MatchesTable.sportEmoji] = sportEmoji
            it[MatchesTable.timeLeft] = timeLeft
            it[dateTime] = LocalDateTime.now() // Ili parsiran dateTimeStr
            it[MatchesTable.dateLabel] = dateLabel
            it[MatchesTable.timeRange] = timeRange
            it[MatchesTable.venue] = venue
            it[MatchesTable.address] = address
            it[MatchesTable.lat] = lat
            it[MatchesTable.lng] = lng
            it[MatchesTable.distance] = distance
            it[joined] = 1
            it[MatchesTable.total] = total
            it[MatchesTable.pricePerPerson] = pricePerPerson
            it[MatchesTable.currency] = currency
            it[MatchesTable.hostId] = hostId
            it[MatchesTable.level] = level
            it[MatchesTable.levelSrb] = levelSrb
            it[MatchesTable.urgent] = urgent
            it[rules] = rulesJoined
        } get MatchesTable.id
    }

    // Dohvatanje mečeva na koje je korisnik prijavljen
    suspend fun getJoinedMatches(userId: Int): List<ResultRow> = dbQuery {
        (MatchesTable innerJoin MatchPlayersTable)
            .selectAll()
            .where { MatchPlayersTable.userId eq userId }
            .orderBy(MatchesTable.dateTime to SortOrder.DESC)
            .toList()
    }

    // Dohvatanje mečeva koje korisnik organizuje (hostuje)
    suspend fun getHostedMatches(userId: Int): List<ResultRow> = dbQuery {
        MatchesTable.selectAll()
            .where { MatchesTable.hostId eq userId }
            .orderBy(MatchesTable.dateTime to SortOrder.DESC)
            .toList()
    }

    // Dohvatanje igrača za određeni meč
    suspend fun getMatchPlayers(matchId: Int): List<ResultRow> = dbQuery {
        (MatchPlayersTable innerJoin UsersTable)
            .selectAll()
            .where { MatchPlayersTable.matchId eq matchId }
            .toList()
    }
}