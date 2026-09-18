package com.example.data.responses

import com.example.data.models.UserDto
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto
)
