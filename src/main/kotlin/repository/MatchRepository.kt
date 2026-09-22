package com.example.repository

import com.example.data.models.MatchDto
import com.example.data.models.UserDto
import com.example.db.MatchesTable
import com.example.db.UsersTable
import com.example.service.MatchService
import com.example.service.WebPushService
import com.example.service.chat.ChatService
import org.jetbrains.exposed.sql.ResultRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.filter

class MatchRepository(private val matchService: MatchService = MatchService(),
    private val chatService: ChatService= ChatService()
) {

    // Dohvatanje feed-a sa biznis opcijom "onlyAvailable" (samo nepopunjeni mečevi)
    suspend fun getFeedMatches(
        currentUserId: Int?,
        sport: String?,
        maxDistance: Double?,
        searchQuery: String?,
        onlyAvailable: Boolean = false
    ): List<MatchDto> {
        val rows = matchService.getMatchesFiltered(sport, maxDistance, searchQuery, onlyAvailable)
        return rows.map { mapToMatchDto(it, currentUserId) }
    }

    suspend fun getExploreMatches(currentUserId: Int?, sportFilter: String?): List<MatchDto> {
        val rows = matchService.getAllMatches()

        return rows
            .filter { row ->
                if (sportFilter.isNullOrBlank() || sportFilter.equals("All", ignoreCase = true)) true
                else row[MatchesTable.sport].equals(sportFilter, ignoreCase = true)
            }
            .map { row -> mapToMatchDto(row, currentUserId) }
    }

    suspend fun getMatchById(matchId: Int, currentUserId: Int?): MatchDto? {
        val row = matchService.getMatchById(matchId) ?: return null
        return mapToMatchDto(row, currentUserId)
    }

    // Biznis logika za prijavu na meč: provera dostupnosti i povećanje broja igrača
    suspend fun joinMatch(matchId: Int, userId: Int): Boolean {
        val inserted = matchService.insertMatchPlayer(matchId, userId, isHost = false)
        if (inserted) {
            matchService.updateJoinedCount(matchId, delta = 1)
            chatService.getOrCreateChatForMatch(matchId,"")
        }
        return inserted
    }

    // Biznis logika za odjavu sa meča: brisanje i smanjenje broja igrača
    suspend fun leaveMatch(matchId: Int, userId: Int): Boolean {
        val deleted = matchService.deleteMatchPlayer(matchId, userId)
        if (deleted) {
            matchService.updateJoinedCount(matchId, delta = -1)
        }
        return deleted
    }

    private fun makeDateLabel(dateTime: LocalDateTime): String {
        val matchDate = dateTime.toLocalDate()
        val today = LocalDate.now()

        return when (matchDate) {
            today -> "Danas"
            today.plusDays(1) -> "Sutra"
            else -> matchDate.format(
                DateTimeFormatter.ofPattern("d. M. yyyy.")
            )
        }
    }
    // Biznis logika kreiranja meča: priprema podataka i automatsko dodavanje hosta kao igrača
    suspend fun createMatch(dto: MatchDto, hostId: Int): Int {
         val isoFormatter = java.time.format.DateTimeFormatterBuilder()
            .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart()
            .appendOffsetId()
            .optionalEnd()
            .toFormatter()

        val dateTime = java.time.LocalDateTime.parse(dto.dateTime, isoFormatter)
        val calculatedDateLabel = makeDateLabel(dateTime)

        val newMatchId = matchService.insertMatch(
            dto = dto,
            hostId = hostId,
            dateTime = dateTime,
            dateLabel = calculatedDateLabel
        )

        matchService.insertMatchPlayer(
            matchId = newMatchId,
            userId = hostId,
            isHost = true
        )
        chatService.getOrCreateChatForMatch(matchId = newMatchId,dto.venue)
        WebPushService.notifyUsersAboutNewMatch(
            hostId = hostId,
            sport = dto.sport,
            venue = dto.venue
        )
        return newMatchId
    }

    suspend fun getMyJoinedMatches(userId: Int): List<MatchDto> {
        val rows = matchService.getJoinedMatches(userId)
        return rows.map { mapToMatchDto(it, userId) }
    }

    suspend fun getMyHostedMatches(userId: Int): List<MatchDto> {
        val rows = matchService.getHostedMatches(userId)
        return rows.map { mapToMatchDto(it, userId) }
    }

    suspend fun getPlayersForMatch(matchId: Int): List<UserDto> {
        val rows = matchService.getMatchPlayers(matchId)
        return rows.map { row ->
            UserDto(
                id = row[UsersTable.id].value.toInt(),
                name = row[UsersTable.name],
                email = row[UsersTable.email],
                city = row[UsersTable.city],
                initials = row[UsersTable.initials],
                karma = row[UsersTable.karma],
                showUpRate = row[UsersTable.showUpRate]
            )
        }
    }

    // Biznis logika konverzije baze u DTO objekat (Mapiranje podataka)
    private suspend fun mapToMatchDto(row: ResultRow, currentUserId: Int?): MatchDto {
        val matchId = row[MatchesTable.id].value.toInt()
        val hostId = row[MatchesTable.hostId].value.toInt()
        val hostRow = matchService.getHostUser(hostId)

        val hostName = hostRow?.get(UsersTable.name) ?: "Nepoznat"
        val hostInitials = hostRow?.get(UsersTable.initials) ?: "MU"

        val rawRules = row[MatchesTable.rules]
        val rulesList = if (rawRules.isNotEmpty()) rawRules else emptyList()

        val isMyMatch = if (currentUserId != null) {
            matchService.isUserJoined(matchId, currentUserId)
        } else false

        return MatchDto(
            id = matchId,
            sport = row[MatchesTable.sport],
            sportEmoji = row[MatchesTable.sportEmoji],
            timeLeft = row[MatchesTable.timeLeft],
            dateTime = row[MatchesTable.dateTime].toString(),
            dateLabel = row[MatchesTable.dateLabel],
            timeRange = row[MatchesTable.timeRange],
            venue = row[MatchesTable.venue],
            address = row[MatchesTable.address],
            lat = row[MatchesTable.lat],
            lng = row[MatchesTable.lng],
            distance = row[MatchesTable.distance],
            joined = row[MatchesTable.joined],
            total = row[MatchesTable.total],
            pricePerPerson = row[MatchesTable.pricePerPerson],
            currency = row[MatchesTable.currency],
            host = hostName,
            hostInitials = hostInitials,
            level = row[MatchesTable.level],
            levelSrb = row[MatchesTable.levelSrb],
            urgent = row[MatchesTable.urgent],
            rules = rulesList,
            isMyMatch = isMyMatch
        )
    }
}