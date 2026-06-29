package io.github.nomisrev

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either

interface Error
interface Database
data class User(val id: Long)

// Dependency
// Raise
context(error: Raise<Error>, db: Database)
suspend fun program(
    userId: Long
): User {
    return User(userId)
}

class Example(val db: Database) {
    suspend fun program(
        userId: Long
    ): Either<Error, User> = either {
        User(userId)
    }
}

suspend fun main() {
    program(1)
}
