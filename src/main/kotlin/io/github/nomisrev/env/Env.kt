package io.github.nomisrev.env

import kotlin.time.Duration
import kotlinx.serialization.Serializable

@Serializable
data class Env(
    val server: Server,
    val datasource: DataSource,
    val auth: Auth,
) {
    @Serializable
    data class Server(
        val host: String,
        val port: Int,
    )

    @Serializable
    data class DataSource(
        val url: String,
        val username: String,
        val password: String,
        val driver: String,
    )

    @Serializable
    data class Auth(
        val secret: String,
        val issuer: String,
        val duration: Duration,
    )
}
