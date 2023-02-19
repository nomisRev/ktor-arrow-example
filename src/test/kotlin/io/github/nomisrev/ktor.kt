@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev

import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.kotlinXSerializersModule
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplication
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json


suspend fun withService(test: suspend HttpClient.(dep: Dependencies) -> Unit): Unit {
  val dependencies = KotestProject.dependencies.get()
  testApplication {
    application { app(dependencies) }
    createClient {
      expectSuccess = false
      install(ContentNegotiation) { json(Json { serializersModule = kotlinXSerializersModule }) }
    }.use { client -> test(client, dependencies) }
  }
}

// Small optimisation to avoid runBlocking from Ktor impl
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private suspend fun testApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
  val builder = ApplicationTestBuilder().apply { block() }
  val testApplication = TestApplication(builder)
  testApplication.engine.start()
  testApplication.stop()
}
