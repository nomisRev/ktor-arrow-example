import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  application
  alias(libs.plugins.kotest.multiplatform)
  id(libs.plugins.kotlin.jvm.pluginId)
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
    dialect(libs.sqldelight.postgresql.asString())
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
      jvmTarget = "${JavaVersion.VERSION_11}"
      freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
  }

  test {
    useJUnitPlatform()
    extensions.configure(kotlinx.kover.api.KoverTaskExtension::class) {
      includes = listOf("io.github.nomisrev.*")
    }
  }
}

dependencies {
  implementation(libs.bundles.arrow)
  implementation(libs.bundles.ktor.server)
  implementation(libs.kjwt.core)
  implementation(libs.ktor.serialization)
  implementation(libs.logback.classic)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)
  implementation(libs.slugify)
  implementation(libs.bcrypt)

  testImplementation(libs.bundles.ktor.client)
  implementation(libs.testcontainers.postgresql)
  implementation("org.testcontainers:mysql:1.17.2")
  implementation("mysql:mysql-connector-java:8.0.29")
  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.bundles.kotest)
}
