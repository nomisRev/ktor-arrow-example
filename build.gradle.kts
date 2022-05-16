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
  id("com.google.cloud.tools.jib") version "3.2.1"
}

application {
  mainClass by "io.github.nomisrev.MainKt"
}

sqldelight {
  database("SqlDelight") {
    packageName = "io.github.nomisrev.sqldelight"
    dialect = "app.cash.sqldelight:postgresql-dialect:2.0.0-alpha02"
  }
}

allprojects {
  extra.set("dokka.outputDirectory", rootDir.resolve("docs"))
  setupDetekt()
}

repositories {
  mavenCentral()
}

jib {
  from {
    image = "openjdk:8-jre-slim"
  }
  container {
    ports = listOf("8080")
    mainClass = "io.github.nomisrev.MainKt"
  }
  to {
    image = "nomisrev/${project.name}"
    tags = setOf("0.0.1")
  }
}

tasks {
  val copyJarToDockerDir by creating(Copy::class) {
    from("$buildDir/libs")
    include("**/*.jar")
    into("$buildDir/docker")
    dependsOn(build)
  }

  val preparedDockerDir by creating(Copy::class) {
    from(file("Dockerfile"))
    into("$buildDir/docker")
  }

  val buildDockerImage by creating(Exec::class) {
    workingDir = buildDir
    commandLine("docker", "build", "-t", "nomisrev/${project.name}:${project.version}", "docker")
    dependsOn(preparedDockerDir, copyJarToDockerDir)
  }

  val runDockerImage by creating(Exec::class) {
    commandLine("docker", "run", "-p", "8080:8080", "nomisrev/${project.name}:${project.version}", "docker")
    dependsOn(buildDockerImage)
  }

  val pushDockerImage by creating(Exec::class) {
    commandLine("docker", "push", "nomisrev/${project.name}:${project.version}")
    dependsOn(buildDockerImage)
  }

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
  implementation(libs.kjwt.core)
  implementation(libs.ktor.serialization)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.defaultheaders)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.auth)
  implementation(libs.logback.classic)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)
  implementation(libs.slugify)

  testImplementation(libs.ktor.client.content.negotiation)
  testImplementation(libs.ktor.client.serialization)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.kotest.arrow)
  testImplementation(libs.kotest.runnerJUnit5)
  testImplementation(libs.kotest.frameworkEngine)
  testImplementation(libs.kotest.assertionsCore)
  testImplementation(libs.kotest.property)
}
