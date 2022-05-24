package io.github.nomisrev.env

import java.lang.System.getenv
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val PORT: Int = 8080
private const val JDBC_URL: String = "jdbc:postgresql://localhost:5432/ktor-arrow-example-database"
private const val JDBC_USER: String = "postgres"
private const val JDBC_PW: String = "postgres"
private const val JDBC_DRIVER: String = "org.postgresql.Driver"
private const val AUTH_SECRET: String = "MySuperStrongSecret"
private const val AUTH_ISSUER: String = "KtorArrowExampleIssuer"
private const val AUTH_DURATION: Int = 30

/** Config that is creating from System Env Variables, and default values */
data class Env(
  val dataSource: DataSource = DataSource(),
  val http: Http = Http(),
  val auth: Auth = Auth(),
) {
  data class Http(
    val host: String = getenv("HOST") ?: "0.0.0.0",
    val port: Int = getenv("SERVER_PORT")?.toIntOrNull() ?: PORT,
  )

  data class DataSource(
    val url: String = getenv("POSTGRES_URL") ?: JDBC_URL,
    val username: String = getenv("POSTGRES_USERNAME") ?: JDBC_USER,
    val password: String = getenv("POSTGRES_PASSWORD") ?: JDBC_PW,
    val driver: String = JDBC_DRIVER,
  )

  data class Auth(
    val secret: String = getenv("JWT_SECRET") ?: AUTH_SECRET,
    val issuer: String = getenv("JWT_ISSUER") ?: AUTH_ISSUER,
    val duration: Duration = (getenv("JWT_DURATION")?.toIntOrNull() ?: AUTH_DURATION).days
  )
}
