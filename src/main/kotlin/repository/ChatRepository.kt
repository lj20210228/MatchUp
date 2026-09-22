package com.example.repository

import com.example.service.chat.ChatEntity
import com.example.service.chat.ChatMessageEntity
import com.example.service.chat.ChatService
import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val id: Int,
    val chatId: Int,
    val type: String, // "user", "other", "system"
    val senderId: Int? = null,
    val sender: String? = null,
    val senderInitials: String? = null,
    val text: String,
    val time: String
)

class ChatRepository(
    private val chatService: ChatService = ChatService()
) {

    // Dohvatanje istorije direktno po chatId-u
    suspend fun getChatHistory(
        chatId: Int,
        currentUserId: Int
    ): List<MessageResponse> {
        val messageEntities = chatService.getMessagesByChatId(chatId)

        // Odmah označavamo da je korisnik pročitao poruke do poslednje
        chatService.markChatAsRead(
            chatId = chatId,
            userId = currentUserId
        )

        return messageEntities.map { entity ->
            mapToResponse(entity, currentUserId)
        }
    }

    // Čuvanje nove poruke
    suspend fun processAndSaveMessage(
        chatId: Int,
        senderId: Int,
        text: String
    ): MessageResponse {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Poruka ne može biti prazna" }

        val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

        val savedEntity = chatService.insertMessage(
            chatId = chatId,
            type = "user",
            senderId = senderId,
            text = cleanText,
            time = currentTime
        )

        // Pošiljalac automatski označava čat kao pročitan za sebe
        chatService.markChatAsRead(chatId = chatId, userId = senderId)

        return mapToResponse(savedEntity, currentUserId = senderId)
    }

    suspend fun markAsRead(chatId: Int, userId: Int) {
        chatService.markChatAsRead(chatId, userId)
    }

    suspend fun getUserChats(userId: Int): List<ChatEntity> {
        return chatService.getChatsForUser(userId)
    }

    private fun mapToResponse(entity: ChatMessageEntity, currentUserId: Int): MessageResponse {
        val calculatedType = when {
            entity.type == "system" -> "system"
            entity.senderId == currentUserId -> "user"
            else -> "other"
        }

        return MessageResponse(
            id = entity.id,
            chatId = entity.chatId,
            type = calculatedType,
            senderId = entity.senderId,
            sender = entity.senderName,
            senderInitials = entity.senderInitials,
            text = entity.text,
            time = entity.createdAt
        )
    }
}