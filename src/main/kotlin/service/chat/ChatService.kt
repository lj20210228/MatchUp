package com.example.service.chat


import com.example.data.models.MessageEntity
import com.example.db.ChatMessagesTable
import com.example.db.ChatsTable
import com.example.db.UsersTable
import com.example.db.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.insertAndGetId // ili insertGetId
// Sirovi entiteti iz baze (mapiraju tabele)
data class ChatEntity(
    val id: Int,
    val matchId: Int,
    val name: String,
    val unread: Int
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
}