package com.example.main

import com.example.configureHttp
import com.example.routes.configureRouting
import com.example.configureSecurity
import com.example.db.UsersTable
import com.example.db.configureDatabases
import com.example.db.dbQuery
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.websocket.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll

import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    // 2. WebSockets konfiguracija za chat
    install(WebSockets) {
        pingPeriod= 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // 3. Poziv tvojih generisanih konfiguracija
    configureHttp()        // CORS
    configureSecurity()    // JWT Auth
    configureDatabases()
    runBlocking {
        try {
            val userCount = dbQuery { UsersTable.selectAll().count() }
            log.info("Baza je povezana! Broj korisnika u bazi: $userCount")
        } catch (e: Exception) {
            log.error("Greška pri radu sa bazom: ${e.message}")
        }
    }



    configureRouting()     // Osnovne rute i WebSockets
}