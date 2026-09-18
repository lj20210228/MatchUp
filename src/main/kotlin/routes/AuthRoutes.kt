package com.example.routes


import com.example.data.requests.LoginRequest
import com.example.data.requests.RegisterRequest
import com.example.repository.AuthRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAuthRoutes() {
    val authRepository = AuthRepository()

    routing {
        route("/api/auth") {

            post("/login") {
                val request = call.receive<LoginRequest>()
                val authResponse = authRepository.login(request)

                if (authResponse != null) {
                    call.respond(HttpStatusCode.OK, authResponse)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Neispravan email ili lozinka"))
                }
            }

            post("/register") {
                try {
                    val request = call.receive<RegisterRequest>()
                    val authResponse = authRepository.register(request)
                    call.respond(HttpStatusCode.Created, authResponse)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("message" to e.message))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Neispravni podaci za registraciju"))
                }
            }

            post("/logout") {
                // Pošto koristimo stateless JWT, logout samo vraća OK status
                // dok klijent briše token iz localStorage-a
                call.respond(HttpStatusCode.OK, mapOf("message" to "Uspešno ste se odjavili"))
            }
        }
    }
}