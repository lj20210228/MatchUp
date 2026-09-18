package com.example.service

import com.example.data.models.UserDto
import com.example.db.UsersTable
import com.example.db.dbQuery
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class UserService {

    suspend fun findByEmailAndPassword(email: String, passwordHash: String): UserDto? = dbQuery {
        UsersTable.selectAll()
            .where { (UsersTable.email eq email) and (UsersTable.passwordHash eq passwordHash) }
            .map { row ->
                UserDto(
                    id = row[UsersTable.id],
                    name = row[UsersTable.name],
                    email = row[UsersTable.email],
                    city = row[UsersTable.city],
                    initials = row[UsersTable.initials],
                    karma = row[UsersTable.karma],
                    showUpRate = row[UsersTable.showUpRate]
                )
            }
            .singleOrNull()
    }

    suspend fun findByEmail(email: String): UserDto? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .map { row ->
                UserDto(
                    id = row[UsersTable.id],
                    name = row[UsersTable.name],
                    email = row[UsersTable.email],
                    city = row[UsersTable.city],
                    initials = row[UsersTable.initials],
                    karma = row[UsersTable.karma],
                    showUpRate = row[UsersTable.showUpRate]
                )
            }
            .singleOrNull()
    }

    suspend fun createUser(
        name: String,
        email: String,
        passwordHash: String,
        city: String,
        initials: String
    ): UserDto = dbQuery {
        val insertedId = UsersTable.insert {
            it[UsersTable.name] = name
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.city] = city
            it[UsersTable.initials] = initials
            it[karma] = 5.0
            it[showUpRate] = 100
        } get UsersTable.id

        UserDto(
            id = insertedId,
            name = name,
            email = email,
            city = city,
            initials = initials,
            karma = 5.0,
            showUpRate = 100
        )
    }
}