package io.github.nomisrev

import arrow.core.raise.context.Raise
import arrow.core.raise.recover
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.users.UserId
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder

data class RegisteredUser(val user: UserFixture, val token: JwtToken, val userId: UserId)

fun HttpMessageBuilder.tokenAuth(token: String): Unit =
    header(HttpHeaders.Authorization, "Token $token")

inline fun <E> assertRaised(block: Raise<E>.() -> Unit): E =
    recover({
        block()
        throw AssertionError("Expected erro to be raised")
    }) { e ->
        e
    }
