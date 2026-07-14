package io.github.nomisrev

import arrow.core.raise.recover
import io.github.nomisrev.users.NewUser
import kotlin.uuid.Uuid

data class UserFixture(val username: Username, val email: Email, val password: Password) {
    fun toNewUser() = NewUser(username.value, email.value, password.raw())
}

data class ArticleFixture(
    val title: Title,
    val description: Description,
    val body: Body,
    val tags: Set<String>,
)

fun userFixture(password: String = "Aa123456!"): UserFixture {
    val suffix = randomSuffix()
    val username = "user-$suffix"
    return recover({
        UserFixture(
            username = Username(username),
            email = Email("$username@domain.com"),
            password = Password(password),
        )
    }) { error ->
        throw RuntimeException("Failed to UserFixture ArticleFixture: $error")
    }
}

fun articleFixture(): ArticleFixture {
    val suffix = randomSuffix()
    return recover({
        ArticleFixture(
            title = Title("Article $suffix"),
            description = Description("Description $suffix"),
            body = Body("Body $suffix"),
            tags = setOf("arrow-$suffix", "ktor-$suffix", "kotlin-$suffix", "sqldelight-$suffix"),
        )
    }) { error ->
        throw RuntimeException("Failed to created ArticleFixture: $error")
    }
}

fun randomSuffix(length: Int = 12): String = Uuid.random().toString().replace("-", "").take(length)
