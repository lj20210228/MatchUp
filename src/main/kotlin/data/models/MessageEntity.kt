package com.example.data.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
@Serializable
data class MessageEntity(
    val id: Int,
    val matchId: Int,
    val senderId: Int,
    val senderFullName: String,
    val text: String,
    @Contextual
    val createdAt: LocalDateTime
)