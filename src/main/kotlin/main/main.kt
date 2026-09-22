package com.example.main

import com.example.configureHttp
import com.example.configureRouting
import com.example.configureSecurity
import com.example.db.ChatMessagesTable
import com.example.db.ChatReadStatesTable
import com.example.db.ChatsTable
import com.example.db.MatchPlayersTable
import com.example.db.MatchesTable
import com.example.db.PushSubscriptionsTable
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
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    // Render dodeljuje port dinamički preko PORT varijable okruženja
   // val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val port=8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    // 2. WebSockets konfiguracija za chat
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // 3. Poziv tvojih generisanih konfiguracija
    configureHttp()        // CORS
    configureSecurity()    // JWT Auth
    configureDatabases()

    // 4. Automatsko kreiranje tabela u bazi ako ne postoje
    transaction {
        SchemaUtils.create(
            UsersTable,
            MatchesTable,
            MatchPlayersTable,
            ChatsTable,
            ChatMessagesTable,
            ChatReadStatesTable,
            PushSubscriptionsTable
        )
    }

    // 5. Test provera konekcije
    runBlocking {
        try {
            val userCount = dbQuery { UsersTable.selectAll().count() }
            log.info("Baza je uspešno povezana i sinhronizovana! Broj korisnika: $userCount")
        } catch (e: Exception) {
            log.error("Greška pri radu sa bazom: ${e.message}")
        }
    }

    configureRouting()     // Osnovne rute i WebSockets
}