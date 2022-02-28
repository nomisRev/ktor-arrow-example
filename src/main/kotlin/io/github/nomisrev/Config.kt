package io.github.nomisrev

data class Config(val dataSource: DataSource, val http: Http, val auth: Auth) {
  data class Http(val host: String, val port: Int)

  data class DataSource(
    val url: String,
    val username: String,
    val password: String,
    val driver: String = "org.postgresql.Driver"
  )

  data class Auth(val secret: String, val issuer: String)
}

private const val DEFAULT_PORT: Int = 8080

fun envConfig(): Config = Config(envDataSource(), envHttp(), envAuth())

fun envHttp(): Config.Http =
  Config.Http(
    host = System.getenv("HOST") ?: "0.0.0.0",
    port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
  )

fun envDataSource(): Config.DataSource =
  Config.DataSource(
    url = System.getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/simonvergauwen",
    username = System.getenv("POSTGRES_USERNAME") ?: "postgres",
    password = System.getenv("POSTGRES_PASSWORD") ?: "postgres",
  )

fun envAuth(): Config.Auth =
  Config.Auth(
    secret = System.getenv("JWT_SECRET") ?: "MySuperStrongSecret",
    issuer = System.getenv("JWT_ISSUER") ?: "KtorArrowExampleIssuer",
  )
