package io.github.nomisrev

import kotlin.uuid.Uuid

data class UserFixture(val username: String, val email: String, val password: String)

data class ArticleFixture(
    val title: String,
    val description: String,
    val body: String,
    val tags: Set<String>,
)

fun userFixture(password: String = "123456789"): UserFixture {
    val suffix = randomSuffix()
    val username = "user-$suffix"
    return UserFixture(username = username, email = "$username@domain.com", password = password)
}

fun articleFixture(): ArticleFixture {
    val suffix = randomSuffix()
    return ArticleFixture(
        title = "Article $suffix",
        description = "Description $suffix",
        body = "Body $suffix",
        tags = setOf("arrow-$suffix", "ktor-$suffix", "kotlin-$suffix", "sqldelight-$suffix"),
    )
}

fun randomSuffix(length: Int = 12): String =
    Uuid.random().toString().replace("-", "").take(length)
