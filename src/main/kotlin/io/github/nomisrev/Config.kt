package io.github.nomisrev

data class Config(val dataSource: DataSource, val host: String, val port: Int)

data class DataSource(
  val url: String,
  val username: String,
  val password: String,
  val driver: String = "org.postgresql.Driver",
  val maximumPoolSize: Int = 10
)

private const val DEFAULT_PORT: Int = 8080

fun envConfig(): Config =
  Config(
    dataSource = envDataSource(),
    host = System.getenv("HOST") ?: "0.0.0.0",
    port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
  )

fun envDataSource(): DataSource =
  DataSource(
    url = System.getenv("POSTGRES_URL")
        ?: "jdbc:postgresql://localhost:5432/ktor-arrow-example-database",
    username = System.getenv("POSTGRES_USERNAME") ?: "postgres",
    password = System.getenv("POSTGRES_PASSWORD") ?: "postgres",
  )
