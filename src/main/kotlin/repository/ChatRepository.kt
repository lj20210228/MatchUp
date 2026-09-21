package com.example.repository


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

    // Biznis logika: Učitavanje istorije poruka i formatiranje u DTO
    suspend fun getChatHistory(matchId: Int, matchName: String, currentUserId: Int): List<MessageResponse> {
        val chat = chatService.getOrCreateChatForMatch(matchId, matchName)
        val messageEntities = chatService.getMessagesByChatId(chat.id)

        return messageEntities.map { entity ->
            mapToResponse(entity, currentUserId)
        }
    }

    // Biznis logika: Obrada nove dolazne poruke sa frontenda i čuvanje
    suspend fun processAndSaveMessage(
        matchId: Int,
        matchName: String,
        senderId: Int,
        text: String
    ): MessageResponse {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Poruka ne može biti prazna" }

        val chat = chatService.getOrCreateChatForMatch(matchId, matchName)

        // Generisanje formata vremena "HH:mm"
        val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

        val savedEntity = chatService.insertMessage(
            chatId = chat.id,
            type = "user",
            senderId = senderId,
            text = cleanText,
            time = currentTime
        )

        return mapToResponse(savedEntity, currentUserId = senderId)
    }

    // Biznis logika: Određivanje da li je poruka "user", "other" ili "system"
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