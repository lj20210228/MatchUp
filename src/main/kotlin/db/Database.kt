package com.example.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Application.configureDatabases() {
    // Izvlačimo iz okruženja (Render/Supabase) ili koristimo fallback za lokalni rad
   val dbHost = System.getenv("DB_HOST") ?: "localhost"
    val dbPort = System.getenv("DB_PORT") ?: "5432"
    val dbName = System.getenv("DB_NAME") ?: "match_up"
    val user = System.getenv("DB_USER") ?: "postgres"
    val password = System.getenv("DB_PASSWORD") ?: "Uzice10072002,"

    // Supabase zahtijeva sslmode=require i prepareThreshold=0 zbog PgBouncer Transaction Poolera
    val sslMode = if (dbHost != "localhost") {
        "?sslmode=require&prepareThreshold=0"
    } else {
        "?currentSchema=public"
    }

    val url = "jdbc:postgresql://$dbHost:$dbPort/$dbName$sslMode"

    log.info("Connecting to Postgres database at $url")

    val config = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = url
        username = user
        this.password = password
        // Ograničavamo pool size (5 za Supabase produkciju, 10 ako je lokalno)
        maximumPoolSize = if (dbHost != "localhost") 5 else 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    /*


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
*/
    Database.connect(HikariDataSource(config))
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }