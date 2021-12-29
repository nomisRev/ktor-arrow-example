val ktor_version: String = "2.0.0-beta-1"
val kotlin_version: String = "1.6.10"
val logback_version: String = "1.2.10"
val arrow_version: String = "1.0.1"

plugins {
  application
  kotlin("jvm") version "1.6.10"
  id("org.jetbrains.kotlin.plugin.serialization") version "1.6.10"
}

group = "io.github.nomisrev"
version = "0.0.1"

application {
  mainClass.set("io.github.nomisrev.ApplicationKt")
}

repositories {
  mavenCentral()
  maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

dependencies {
  implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
  implementation("io.ktor:ktor-server-core:$ktor_version")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
  implementation("io.ktor:ktor-server-netty:$ktor_version")
  implementation("ch.qos.logback:logback-classic:$logback_version")
  implementation("io.arrow-kt:arrow-core:$arrow_version")

  testImplementation("io.ktor:ktor-server-tests:$ktor_version")
  testImplementation("io.ktor:ktor-client-serialization:$ktor_version")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}