package com.example.service.chat


import com.example.data.models.MessageEntity
import com.example.db.ChatMessagesTable
import com.example.db.ChatsTable
import com.example.db.MatchPlayersTable
import com.example.db.UsersTable
import com.example.db.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.insertAndGetId // ili insertGetId
import kotlin.collections.emptyList
import com.example.db.ChatReadStatesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

// Sirovi entiteti iz baze (mapiraju tabele)
@Serializable
data class ChatEntity(
    val id: Int,
    val matchId: Int,
    val name: String,
    val unread: Int,

)

data class ChatMessageEntity(
    val id: Int,
    val chatId: Int,
    val type: String,
    val senderId: Int?,
    val senderName: String?,
    val senderInitials: String?,
    val text: String,
    val createdAt: String
)

class ChatService {

    // READ: Pronađi ili kreiraj chat za određeni matchId
    suspend fun getOrCreateChatForMatch(matchId: Int, matchName: String): ChatEntity = dbQuery {
        val existing = ChatsTable.select { ChatsTable.matchId eq matchId }.singleOrNull()
        if (existing != null) {
            ChatEntity(
                id = existing[ChatsTable.id].value.toInt(),
                matchId = existing[ChatsTable.matchId].value,
                name = existing[ChatsTable.name],
                unread = existing[ChatsTable.unread]
            )
        } else {
            val newId = ChatsTable.insertAndGetId {
                it[ChatsTable.matchId] = matchId
                it[ChatsTable.name] = matchName
                it[ChatsTable.unread] = 0
            }.value

            ChatEntity(id = newId, matchId = matchId, name = matchName, unread = 0)
        }
    }

    // READ: Sve poruke iz određenog chata (JOIN sa UsersTable za ime i inicijale)
    suspend fun getMessagesByChatId(chatId: Int): List<ChatMessageEntity> = dbQuery {
        (ChatMessagesTable leftJoin UsersTable)
            .select { ChatMessagesTable.chatId eq chatId }
            .orderBy(ChatMessagesTable.id to SortOrder.ASC)
            .map { row ->
                ChatMessageEntity(
                    id = row[ChatMessagesTable.id].value,
                    chatId = row[ChatMessagesTable.chatId].value,
                    type = row[ChatMessagesTable.type],
                    senderId = row[ChatMessagesTable.senderId]?.value,
                    senderName = row.getOrNull(UsersTable.name),
                    senderInitials = row.getOrNull(UsersTable.initials),
                    text = row[ChatMessagesTable.text],
                    createdAt = row[ChatMessagesTable.createdAt]
                )
            }
    }

    // CREATE: Upis nove poruke u ChatMessagesTable
    suspend fun insertMessage(
        chatId: Int,
        type: String,
        senderId: Int?,
        text: String,
        time: String
    ): ChatMessageEntity = dbQuery {
        val msgId= ChatMessagesTable.insertAndGetId {
            it[ChatMessagesTable.chatId] = chatId
            it[ChatMessagesTable.type] = type
            it[ChatMessagesTable.senderId] = senderId
            it[ChatMessagesTable.text] = text
            it[ChatMessagesTable.createdAt] = time
        }.value

        val senderName = senderId?.let { id ->
            UsersTable.select { UsersTable.id eq id }.singleOrNull()?.get(UsersTable.name)
        }
        val senderInitials = senderId?.let { id ->
            UsersTable.select { UsersTable.id eq id }.singleOrNull()?.get(UsersTable.initials)
        }

        ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            type = type,
            senderId = senderId,
            senderName = senderName,
            senderInitials = senderInitials,
            text = text,
            createdAt = time
        )
    }

    suspend fun getChatsForUser(currentUserId: Int): List<ChatEntity> = dbQuery {
        val matchIds = MatchPlayersTable
            .select { MatchPlayersTable.userId eq currentUserId }
            .map { it[MatchPlayersTable.matchId] }
            .distinct()

        if (matchIds.isEmpty()) {
            return@dbQuery emptyList()
        }

        ChatsTable
            .select { ChatsTable.matchId inList matchIds }
            .map { row ->
                val chatId = row[ChatsTable.id].value

                val readState = ChatReadStatesTable.select {
                    (ChatReadStatesTable.chatId eq chatId) and
                            (ChatReadStatesTable.userId eq currentUserId)
                }.singleOrNull()

                val lastReadMessageId =
                    readState?.get(ChatReadStatesTable.lastReadMessageId) ?: 0

                val unreadCount = ChatMessagesTable.select {
                    (ChatMessagesTable.chatId eq chatId) and
                            (ChatMessagesTable.id greater lastReadMessageId) and
                            (
                                    ChatMessagesTable.senderId.isNull() or
                                            (ChatMessagesTable.senderId neq currentUserId)
                                    )
                }.count().toInt()

                ChatEntity(
                    id = chatId,
                    matchId = row[ChatsTable.matchId].value,
                    name = row[ChatsTable.name],
                    unread = unreadCount
                )
            }
    }
    suspend fun getUnreadCount(chatId: Int, userId: Int): Int = dbQuery {
        val readState = ChatReadStatesTable.select {
            (ChatReadStatesTable.chatId eq chatId) and
                    (ChatReadStatesTable.userId eq userId)
        }.singleOrNull()

        val lastReadMessageId = readState?.get(ChatReadStatesTable.lastReadMessageId) ?: 0

        ChatMessagesTable.select {
            (ChatMessagesTable.chatId eq chatId) and
                    (ChatMessagesTable.id greater lastReadMessageId) and
                    (
                            ChatMessagesTable.senderId.isNull() or
                                    (ChatMessagesTable.senderId neq userId)
                            )
        }.count().toInt()
    }

    suspend fun markChatAsRead(chatId: Int, userId: Int) = dbQuery {
        val lastMessageId = ChatMessagesTable
            .select { ChatMessagesTable.chatId eq chatId }
            .orderBy(ChatMessagesTable.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(ChatMessagesTable.id)
            ?.value
            ?: return@dbQuery

        val updatedRows = ChatReadStatesTable.update({
            (ChatReadStatesTable.chatId eq chatId) and
                    (ChatReadStatesTable.userId eq userId)
        }) {
            it[lastReadMessageId] = lastMessageId
        }

        if (updatedRows == 0) {
            ChatReadStatesTable.insert {
                it[ChatReadStatesTable.chatId] = chatId
                it[ChatReadStatesTable.userId] = userId
                it[ChatReadStatesTable.lastReadMessageId] = lastMessageId
            }
        }
    }
}