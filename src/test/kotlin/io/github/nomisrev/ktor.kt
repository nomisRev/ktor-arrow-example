@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev

import arrow.core.raise.recover
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.kotlinXSerializersModule
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.SlugGenerator
import io.github.nomisrev.service.register
import io.github.nomisrev.service.slugifyGenerator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

suspend fun <A> withDependencies(
  block: suspend context(Env.Auth, UserPersistence) () -> A
): A {
  val dependencies = KotestProject.dependencies.get()
  return block(KotestProject.env.auth, dependencies.userPersistence)
}

suspend fun withServer(test: suspend context(
  HttpClient,
  Env.Auth,
  UserPersistence,
  ArticlePersistence,
  TagPersistence,
  SlugGenerator
) () -> Unit): Unit {
  val dependencies = KotestProject.dependencies.get()
  testApplication {
    application { app(KotestProject.env, dependencies) }
    createClient {
      expectSuccess = false
      install(ContentNegotiation) { json(Json { serializersModule = kotlinXSerializersModule }) }
      install(Resources) { serializersModule = kotlinXSerializersModule }
    }
      .use { client ->
        test(
          client,
          KotestProject.env.auth,
          dependencies.userPersistence,
          dependencies.articlePersistence,
          dependencies.tagPersistence,
          slugifyGenerator(),
        )
      }
  }
}

suspend fun registerUser(name: String): JwtToken =
  with(KotestProject.env.auth) {
    with(KotestProject.dependencies.get().userPersistence) {
      recover({
        register(RegisterUser(name, "$name@email.com", "password"))
      }) { throw AssertionError("Registering user: $name failed", RuntimeException(it.toString())) }
    }
  }
