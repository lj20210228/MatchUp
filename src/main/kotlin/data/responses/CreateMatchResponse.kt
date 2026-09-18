package com.example.data.responses

import kotlinx.serialization.Serializable

@Serializable
data class CreateMatchResponse(
    val id: Int,
    val message: String
)
