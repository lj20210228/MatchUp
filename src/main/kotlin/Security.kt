package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

fun Application.configureSecurity() {
    val jwtSecret = "matchup_secret_key_123"
    val jwtIssuer = "http://0.0.0.0:8080/"
    val jwtAudience = "matchup_users"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "matchup"

            authHeader { call ->
                val queryToken = call.request.queryParameters["token"]

                if (!queryToken.isNullOrBlank()) {
                    HttpAuthHeader.Single("Bearer", queryToken)
                } else {
                    val authorization =
                        call.request.headers[HttpHeaders.Authorization]

                    authorization?.let {
                        runCatching {
                            parseAuthorizationHeader(it)
                        }.getOrNull()
                    }
                }
            }

            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )

            validate { credential ->
                val userId = credential.payload
                    .getClaim("userId")
                    .asInt()

                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}