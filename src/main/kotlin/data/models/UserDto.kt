package com.example.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val city: String,
    val initials: String,
    val karma: Double,
    val showUpRate: Int
)
