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
  withType<KotlinCompile>().configureEach {
    kotlin.compilerOptions.freeCompilerArgs.add("-Xcontext-receivers")
  }

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
    ktfmt().googleStyle().configure {
      it.setRemoveUnusedImports(true)
      it.setManageTrailingCommas(true)
    }
  }
}

dependencies {
  implementation(libs.bundles.arrow)
  implementation(libs.bundles.ktor.server)
  implementation(libs.bundles.suspendapp)
  implementation(libs.kjwt.core)
  implementation(libs.logback.classic)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)
  implementation(libs.slugify)
  implementation(libs.bundles.cohort)

  testImplementation(libs.bundles.ktor.client)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.bundles.kotest)
}
