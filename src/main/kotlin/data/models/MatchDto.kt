package com.example.data.models

import kotlinx.serialization.Serializable


@Serializable
data class MatchDto(
    val id: Int? = null,
    val sport: String,
    val sportEmoji: String,
    val timeLeft: String = "Uskoro",
    val dateTime: String,
    val dateLabel: String,
    val timeRange: String,
    val venue: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distance: Double = 0.0,
    val joined: Int = 1,
    val total: Int,
    val pricePerPerson: Int = 0,
    val currency: String = "RSD",
    val host: String = "",
    val hostInitials: String = "",
    val level: String = "Intermediate",
    val levelSrb: String = "Rekreativno",
    val urgent: Boolean = false,
    val rules: List<String> = emptyList(),
    val isMyMatch: Boolean = false
)