@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev

import io.github.nomisrev.env.Env
import io.github.nomisrev.env.kotlinXSerializersModule
import io.github.nomisrev.repo.UserPersistence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

suspend fun <A> withDependencies(
  block: suspend context(Env.Auth, UserPersistence) () -> A
): A {
  val dependencies = KotestProject.dependencies.get()
  return block(KotestProject.env.auth, dependencies.userPersistence)
}

suspend fun withService(test: suspend context(HttpClient, Env.Auth, UserPersistence) () -> Unit): Unit {
  val dependencies = KotestProject.dependencies.get()
  testApplication {
    application { app(KotestProject.env, dependencies) }
    createClient {
        expectSuccess = false
        install(ContentNegotiation) { json(Json { serializersModule = kotlinXSerializersModule }) }
      }
      .use { client -> test(client, KotestProject.env.auth, dependencies.userPersistence) }
  }
}
