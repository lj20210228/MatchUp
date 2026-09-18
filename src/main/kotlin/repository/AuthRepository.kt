package com.example.repository


import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.data.*
import com.example.data.models.UserDto
import com.example.data.requests.LoginRequest
import com.example.data.requests.RegisterRequest
import com.example.data.responses.AuthResponse
import com.example.service.UserService
import java.security.MessageDigest
import java.util.*

class AuthRepository(private val userService: UserService = UserService()) {

    private val jwtSecret = "matchup_secret_key_123"
    private val jwtIssuer = "http://0.0.0.0:8080/"
    private val jwtAudience = "matchup_users"
    private val validityInMs = 3600_000L * 24 * 7 // 7 dana

    suspend fun login(request: LoginRequest): AuthResponse? {
        val hashedPass = hashPassword(request.password)
        val user = userService.findByEmailAndPassword(request.email, hashedPass) ?: return null
        val token = generateToken(user)
        return AuthResponse(token = token, user = user)
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        val existingUser = userService.findByEmail(request.email)
        if (existingUser != null) {
            throw IllegalArgumentException("Korisnik sa datim email-om već postoji.")
        }

        val hashedPass = hashPassword(request.password)
        val initials = generateInitials(request.name)

        val newUser = userService.createUser(
            name = request.name,
            email = request.email,
            passwordHash = hashedPass,
            city = request.city,
            initials = initials
        )

        val token = generateToken(newUser)
        return AuthResponse(token = token, user = newUser)
    }

    private fun generateToken(user: UserDto): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("userId", user.id)
            .withClaim("email", user.email)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun generateInitials(name: String): String {
        val parts = name.trim().split("\\s+".toRegex())
        return if (parts.size >= 2) {
            "${parts[0].first()}${parts[1].first()}".uppercase()
        } else if (parts.isNotEmpty() && parts[0].length >= 2) {
            parts[0].take(2).uppercase()
        } else {
            "MU"
        }
    }
}