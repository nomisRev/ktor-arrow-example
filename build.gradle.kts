import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  application
  alias(libs.plugins.kotest.multiplatform)
  id(libs.plugins.kotlin.jvm.pluginId)
  alias(libs.plugins.arrowGradleConfig.formatter)
  alias(libs.plugins.dokka)
  id(libs.plugins.detekt.pluginId)
  alias(libs.plugins.kover)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.sqldelight)
}

application {
  mainClass by "io.github.nomisrev.MainKt"
}

sqldelight {
  database("SqlDelight") {
    packageName = "io.github.nomisrev.sqldelight"
    dialect = "postgresql"
  }
}

allprojects {
  extra.set("dokka.outputDirectory", rootDir.resolve("docs"))
  setupDetekt()
}

repositories {
  mavenCentral()
}

tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "1.8"
      freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }

  test {
    useJUnitPlatform()
    extensions.configure(kotlinx.kover.api.KoverTaskExtension::class) {
      includes = listOf("io.github.nomisrev.*")
    }
  }
}

dependencies {
  implementation(libs.arrow.core)
  implementation(libs.arrow.fx)

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.defaultheaders)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.server.html)

  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.client.serialization)
  implementation(libs.ktor.client.content.negotiation)

  implementation(libs.ktor.serialization)

  implementation(libs.logback.classic)

  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)

  implementation(libs.kjwt.core)

  testImplementation(libs.testcontainers.postgresql)

  testImplementation(libs.ktor.server.tests)

  testImplementation(libs.kotest.runnerJUnit5)
  testImplementation(libs.kotest.frameworkEngine)
  testImplementation(libs.kotest.assertionsCore)
  testImplementation(libs.kotest.property)
}
