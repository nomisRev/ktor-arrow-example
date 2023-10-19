import org.graalvm.buildtools.gradle.dsl.NativeImageOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  application
  alias(libs.plugins.kotest.multiplatform)
  id(libs.plugins.kotlin.jvm.pluginId)
  alias(libs.plugins.dokka)
  id(libs.plugins.detekt.pluginId)
  alias(libs.plugins.kover)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.ktor)
  alias(libs.plugins.spotless)
  alias(libs.plugins.graalvm.buildtool)
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

allprojects {
  extra.set("dokka.outputDirectory", rootDir.resolve("docs"))
  setupDetekt()
}

repositories {
  mavenCentral()
}

java {
  sourceCompatibility = JavaVersion.VERSION_19
  targetCompatibility = JavaVersion.VERSION_19
}

tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_19.toString()
      freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
  }

  test {
    useJUnitPlatform()
  }
}

ktor {
  docker {
    jreVersion = JavaVersion.VERSION_19
    localImageName = "ktor-arrow-example"
    imageTag = "latest"
  }
}

spotless {
  kotlin {
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
}

graalvmNative {
  metadataRepository {
    enabled.set(true)
  }
  binaries {
    named("main").configure {
      configureNativeBuild(useQuickBuild = false)
    }
    named("test").configure {
      configureNativeBuild(useQuickBuild = true)
    }
  }
}

fun NativeImageOptions.configureNativeBuild(
  useQuickBuild: Boolean
) = apply {
  verbose.set(true)
  quickBuild.set(useQuickBuild)
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
  })
  buildArgs = listOf(
    "-march=native",
    "--enable-http",
    "--enable-https",
    "--install-exit-handlers",
    "--report-unsupported-elements-at-runtime",
    "--initialize-at-build-time=io.ktor,kotlin,io.github.nomisrev.routes",
    "--initialize-at-build-time=org.slf4j.LoggerFactory",
    "--initialize-at-build-time=ch.qos.logback",
    "--initialize-at-build-time=kotlinx.serialization.modules.SerializersModuleKt",
    "--initialize-at-build-time=kotlinx.serialization.json.Json\$Default",
    "--initialize-at-build-time=kotlinx.serialization.internal.StringSerializer",
    "--initialize-at-build-time=org.fusesource.jansi",
    "-H:+ReportExceptionStackTraces",
  )
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
