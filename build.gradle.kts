import com.diffplug.spotless.kotlin.KtfmtStep
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  application
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.assert)
  alias(libs.plugins.kover)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.kotest.multiplatform)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.ktor)
  alias(libs.plugins.spotless)
  alias(libs.plugins.version.catalog.update)
}

application {
  mainClass = "io.github.nomisrev.MainKt"
}

sqldelight {
  databases {
    create("SqlDelight") {
      packageName = "io.github.nomisrev.sqldelight"
      dialect(libs.sqldelight.postgresql.get())
    }
  }
}

tasks {
  test {
    useJUnitPlatform()
  }
}

ktor {
  docker {
    jreVersion = JavaVersion.VERSION_21
    localImageName = "ktor-arrow-example"
  }
}

spotless {
  kotlin {
    targetExclude("**/build/**")
    ktfmt().kotlinlangStyle().configure {
      it.setRemoveUnusedImports(true)
      it.setTrailingCommaManagementStrategy(KtfmtStep.TrailingCommaManagementStrategy.ONLY_ADD)
    }
  }
}

dependencies {
  implementation(libs.bundles.arrow)
  implementation(ktorLibs.serialization.kotlinx.json)
  implementation(ktorLibs.server.netty)
  implementation(ktorLibs.server.defaultHeaders)
  implementation(ktorLibs.server.cors)
  implementation(ktorLibs.server.contentNegotiation)
  implementation(libs.spine.api)
  implementation(libs.spine.server)
  implementation(libs.spine.server.arrow)
  implementation(ktorLibs.server.auth)
  implementation(libs.kjwt.core)
  implementation(libs.logback.classic)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)
  implementation(libs.slugify)
  implementation(libs.bundles.cohort)

  implementation(ktorLibs.client.contentNegotiation)
  testImplementation(libs.spine.client)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.bundles.kotest)
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    optIn.add("arrow.core.raise.ExperimentalRaiseAccumulateApi")
  }
}