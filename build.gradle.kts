import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  application
  alias(libs.plugins.kotest.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.arrowGradleConfig.kotlin)
  alias(libs.plugins.arrowGradleConfig.formatter)
  alias(libs.plugins.dokka)
  alias(libs.plugins.detekt)
  alias(libs.plugins.kover)
}

infix fun <T> Property<T>.by(value: T) {
  set(value)
}

application {
  mainClass by "io.github.nomisrev.ApplicationKt"
}

allprojects {
  extra.set("dokka.outputDirectory", rootDir.resolve("docs"))
}

repositories {
  mavenCentral()
  maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
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
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.serialization)
  implementation(libs.logback.classic)

  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.kotest.runnerJUnit5)
  testImplementation(libs.kotest.frameworkEngine)
  testImplementation(libs.kotest.assertionsCore)
  testImplementation(libs.kotest.property)
}

detekt {
  buildUponDefaultConfig = true
  allRules = true
}

tasks.withType<Detekt>().configureEach {
  reports {
    html.required by true
    sarif.required by true
    txt.required by false
    xml.required by false
  }
}

