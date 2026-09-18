package com.example.db


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Application.configureDatabases() {
    val url = environment.config.propertyOrNull("postgres.url")?.getString()
        ?: "jdbc:postgresql://localhost:5432/match_up?currentSchema=public"
    val user = environment.config.propertyOrNull("postgres.user")?.getString() ?: "myuser"
    val password = environment.config.propertyOrNull("postgres.password")?.getString() ?: "myuser"

    log.info("Connecting to Postgres database at $url")

    val config = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    Database.connect(HikariDataSource(config))
}

// Helper funkcija za asinhrono izvršavanje Exposed upita u korutinama
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }