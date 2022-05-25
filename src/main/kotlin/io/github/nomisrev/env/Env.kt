package io.github.nomisrev.env

import arrow.core.Ior
import arrow.core.rightIor
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.continuations.ior
import arrow.typeclasses.Semigroup
import java.lang.System.getenv
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val PORT: Int = 8080
private const val JDBC_URL: String = "jdbc:postgresql://localhost:5432/ktor-arrow-example-database"
private const val AUTH_DURATION: Int = 30

/** Config that is creating from System Env Variables, and default values */
data class Env(val dataSource: DataSource, val http: Http, val auth: Auth) {
  data class Http(val host: String, val port: Int)
  data class DataSource(val url: String, val username: String, val password: String, val driver: String)
  data class Auth(val secret: String, val issuer: String, val duration: Duration)
}

suspend fun envOrThrow(): Env = ior.eager<NonEmptyList<String>, Env>(Semigroup.nonEmptyList()) {
  Env(
    Env.DataSource(
      required("POSTGRES_URL", JDBC_URL).bind(),
      required("POSTGRES_USERNAME", "postgres").bind(),
      required("POSTGRES_PASSWORD", "postgres").bind(),
      "org.postgresql.Driver"
    ),
    Env.Http(
      required("HOST", "0.0.0.0").bind(),
      required("SERVER_PORT", PORT) { it.toIntOrNull() }.bind()
    ),
    Env.Auth(
      required("JWT_SECRET", "MySuperStrongSecret").bind(),
      required("JWT_ISSUER", "KtorArrowExampleIssuer").bind(),
      required("JWT_DURATION", AUTH_DURATION) { it.toIntOrNull() }.bind().days
    )
  )
}
  .toEither()
  .getOrHandle { throw IncorrectEnvException(it) }

class IncorrectEnvException(errors: Nel<String>) : RuntimeException() {
  override val message: String = errors.joinToString(separator = "\n")
}

fun required(name: String, default: String): Ior<Nel<String>, String> =
  getenv(name)?.rightIor() ?: Ior.bothNel(name, default)

inline fun <A : Any> required(name: String, default: A, transform: (String) -> A?): Ior<Nel<String>, A> =
  getenv(name)?.let(transform)?.rightIor() ?: Ior.bothNel(name, default)
