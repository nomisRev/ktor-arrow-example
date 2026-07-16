import kotlinx.knit.KnitPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  application
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.assert)
  alias(libs.plugins.kotlinx.kover)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.kotlinx.knit)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.ktor)
  alias(libs.plugins.testballoon)
  alias(libs.plugins.spotless)
  alias(libs.plugins.version.catalog.update)
  alias(libs.plugins.dev.tools)
}

configure<KnitPluginExtension> {
  files = project.fileTree(projectDir) {
    include("docs/tutorials/validation.md")
  }
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

  // Validation tutorial snippets are intentionally partial fragments; Knit keeps them
  // as documentation artifacts rather than test-compilation inputs.
  withType<KotlinCompile>().configureEach {
    exclude("**/knit/examples/example-validation-*.kt")
  }
}

ktor {
  docker {
    jreVersion = JavaVersion.VERSION_21
    localImageName = "ktor-arrow-example"
  }
}

// TODO: re-enable formatting but currently using https://github.com/qwwdfsad/ktfmt/pull/2
//spotless {
//  kotlin {
//    targetExclude("**/build/**", "**/knit/examples/**")
//    ktfmt("0.64").kotlinlangStyle().configure {
//      it.setRemoveUnusedImports(true)
//      it.setTrailingCommaManagementStrategy(KtfmtStep.TrailingCommaManagementStrategy.ONLY_ADD)
//    }
//  }
//}

dependencies {
  implementation(libs.bundles.arrow)
  implementation(ktorLibs.serialization.kotlinx.json)
  implementation(ktorLibs.server.netty)
  implementation(ktorLibs.server.defaultHeaders)
  implementation(ktorLibs.server.cors)
  implementation(ktorLibs.server.contentNegotiation)
  implementation(ktorLibs.server.config.yaml)
  implementation(libs.spine.api)
  implementation(libs.spine.server)
  implementation(libs.spine.server.arrow)
  implementation(ktorLibs.server.auth.jwt)
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
  testImplementation(libs.testballoon.framework.core)
  testImplementation("org.jetbrains.kotlinx:kotlinx-knit-test:0.5.1")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    optIn.add("arrow.core.raise.ExperimentalRaiseAccumulateApi")
    freeCompilerArgs.addAll(
      "-Xreturn-value-checker=full",
      "-Xname-based-destructuring=complete",
      "-Xcontext-sensitive-resolution",
      "-Xcollection-literals",
      "-Xintrinsic-const-evaluation"
    )
    allWarningsAsErrors = true
  }
}