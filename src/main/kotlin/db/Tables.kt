package com.example.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject
import java.sql.Connection

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
class TextArrayColumnType : ColumnType<List<String>>() {
    override fun sqlType(): String = "TEXT[]"

    override fun valueFromDB(value: Any): List<String> {
        return when (value) {
            is java.sql.Array -> (value.array as Array<*>).map { it.toString() }
            is Array<*> -> value.map { it.toString() }
            is String -> value.split(",").map { it.trim('{', '}', '"', ' ') }
            else -> emptyList()
        }
    }

    override fun notNullValueToDB(value: List<String>): Any {
        val pgObj = PGobject()
        pgObj.type = "text[]"
        // Konvertujemo Kotlin List<String> u PostgreSQL array format: {"rule1", "rule2"}
        pgObj.value = "{" + value.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" } + "}"
        return pgObj
    }
}
fun Table.textArray(name: String): Column<List<String>> = registerColumn(name, TextArrayColumnType())

// Promeni sa Table na IntIdTable i ukloni ručnu "val id" definiciju:

object UsersTable : IntIdTable("users") {
    val name = varchar("name", 100)
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val city = varchar("city", 100)
    val initials = varchar("initials", 5)
    val karma = double("karma").default(5.0)
    val showUpRate = integer("show_up_rate").default(100)
}

object MatchesTable : IntIdTable("matches") {
    val sport = varchar("sport", 50)
    val sportEmoji = varchar("sport_emoji", 10)
    val timeLeft = varchar("time_left", 50)
    val dateTime = datetime("date_time")
    val dateLabel = varchar("date_label", 50)
    val timeRange = varchar("time_range", 50)
    val venue = varchar("venue", 150)
    val address = varchar("address", 255)
    val lat = double("lat")
    val lng = double("lng")
    val distance = double("distance")
    val joined = integer("joined").default(1)
    val total = integer("total")
    val pricePerPerson = integer("price_per_person")
    val currency = varchar("currency", 10).default("RSD")
    val hostId = reference("host_id", UsersTable) // Preporučeno za IntIdTable
    val level = varchar("level", 50)
    val levelSrb = varchar("level_srb", 50)
    val urgent = bool("urgent").default(false)
    val rules = textArray("rules")
}

object ChatsTable : IntIdTable("chats") {
    val matchId = reference("match_id", MatchesTable)
    val name = varchar("name", 150)
    val unread = integer("unread").default(0)
}

object ChatMessagesTable : IntIdTable("chat_messages") {
    val chatId = reference("chat_id", ChatsTable)
    val type = varchar("type", 20)
    val senderId = reference("sender_id", UsersTable).nullable()
    val text = text("text")
    val createdAt = varchar("created_at", 10)
}

// Ova tabela ostaje običan Table jer ima kompozitni primarni ključ (nema svoj id)
object MatchPlayersTable : Table("match_players") {
    val matchId = reference("match_id", MatchesTable)
    val userId = reference("user_id", UsersTable)
    val isHost = bool("is_host").default(false)

    override val primaryKey = PrimaryKey(matchId, userId)
}
object ChatReadStatesTable : IntIdTable("chat_read_states") {
    val chatId = reference("chat_id", ChatsTable)
    val userId = reference("user_id", UsersTable)
    val lastReadMessageId = integer("last_read_message_id").nullable()
    val lastReadAt = varchar("last_read_at", 50).nullable()

    init {
        uniqueIndex(chatId, userId)
    }
}

object PushSubscriptionsTable : Table("push_subscriptions") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id")
    val endpoint = text("endpoint").uniqueIndex()
    val p256dh = text("p256dh")
    val auth = text("auth")

    override val primaryKey = PrimaryKey(id)
}