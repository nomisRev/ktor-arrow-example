package io.github.nomisrev

import arrow.core.raise.recover
import kotlin.uuid.Uuid

data class UserFixture(val username: String, val email: String, val password: String)

data class ArticleFixture(
    val title: Title,
    val description: Description,
    val body: Body,
    val tags: Set<String>,
)

fun userFixture(password: String = "Aa123456!"): UserFixture {
    val suffix = randomSuffix()
    val username = "user-$suffix"
    return UserFixture(username = username, email = "$username@domain.com", password = password)
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
        throw RuntimeException("Impossible")
    }
}

fun randomSuffix(length: Int = 12): String = Uuid.random().toString().replace("-", "").take(length)
