package com.example.db


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Application.configureDatabases() {
    // Izvlačimo iz okruženja (Render/System) ili fallback na lokalne vrednosti
    val dbHost = System.getenv("DB_HOST") ?: "localhost"
    val dbPort = System.getenv("DB_PORT") ?: "5432"
    val dbName = System.getenv("DB_NAME") ?: "match_up"
    val user = System.getenv("DB_USER") ?: "postgres"
    val password = System.getenv("DB_PASSWORD") ?: "Uzice10072002,"

    // Supabase zahteva sslmode=require u JDBC URL-u
    val sslMode = if (dbHost != "localhost") "?sslmode=require" else "?currentSchema=public"
    val url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/match_up?currentSchema=public"
    log.info("Connecting to Postgres database at $url")

    val config = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 5 // Smanjeno na 5 zbog Supabase Free Tier ograničenja
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    Database.connect(HikariDataSource(config))
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }