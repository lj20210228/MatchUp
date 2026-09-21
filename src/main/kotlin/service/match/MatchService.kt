package com.example.service

import com.example.data.models.MatchDto
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
        dto: MatchDto,
        hostId: Int,
        dateTime: LocalDateTime,
        dateLabel: String
    ): Int = dbQuery {
        MatchesTable.insert {
            it[sport] = dto.sport
            it[sportEmoji] = dto.sportEmoji
            it[timeLeft] = dto.timeLeft
            it[MatchesTable.dateTime] = dateTime
            it[MatchesTable.dateLabel] = dateLabel
            it[timeRange] = dto.timeRange
            it[venue] = dto.venue
            it[address] = dto.address
            it[lat] = dto.lat
            it[lng] = dto.lng
            it[distance] = dto.distance
            it[joined] = 1
            it[total] = dto.total
            it[pricePerPerson] = dto.pricePerPerson
            it[currency] = dto.currency
            it[MatchesTable.hostId] = hostId
            it[level] = dto.level
            it[levelSrb] = dto.levelSrb
            it[urgent] = dto.urgent
            it[rules] = dto.rules
        } [MatchesTable.id].value
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