import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
  kotlin("multiplatform")
//  kotlin("native.cocoapods")
}

kotlin {
  jvm()

  iosArm32()
  iosArm64()
  iosSimulatorArm64()
  iosX64()
  macosArm64()
  macosX64()
  tvosArm64()
  tvosSimulatorArm64()
  tvosX64()
  watchosArm32()
  watchosArm64()
  watchosSimulatorArm64()
  watchosX64()
  watchosX86()

//  cocoapods {
//    version = "1.0.0"
//    summary = "Kotlin MPP ...TODO"
//    pod("BCryptSwift")
//  }

  val commonMain by sourceSets.getting
  val iosArm32Main by sourceSets.getting
  val iosArm64Main by sourceSets.getting
  val iosSimulatorArm64Main by sourceSets.getting
  val iosX64Main by sourceSets.getting
  val macosArm64Main by sourceSets.getting
  val macosX64Main by sourceSets.getting
  val tvosArm64Main by sourceSets.getting
  val tvosSimulatorArm64Main by sourceSets.getting
  val tvosX64Main by sourceSets.getting
  val watchosArm32Main by sourceSets.getting
  val watchosArm64Main by sourceSets.getting
  val watchosSimulatorArm64Main by sourceSets.getting
  val watchosX64Main by sourceSets.getting
  val watchosX86Main by sourceSets.getting

  val nativeMain by sourceSets.creating {
    dependsOn(commonMain)
  }
  targets.withType<KotlinNativeTarget> {
    sourceSets["${targetName}Main"].apply {
      dependsOn(nativeMain)
    }
    compilations["main"].apply {
      cinterops.create("libbcrypt") {
        includeDirs("$buildDir/libbcrypt/${konanTarget.name}")
      }
      kotlinOptions.freeCompilerArgs = listOf(
        "-include-binary", "$buildDir/libbcrypt/${konanTarget.name}/libbcrypt.a"
      )
    }
  }

  sourceSets.named("jvmMain") {
    dependencies {
      implementation("at.favre.lib:bycrypt:0.9.0")
    }
  }
}

tasks.withType<CInteropProcess> {
  val archiveFile = File("$buildDir/libbcrypt/${konanTarget.name}", "libbcrypt.zip")

  val downloadArchive = tasks.register<Download>(name.replaceFirst("cinterop", "download")) {
    src("https://github.com/rg3/libbcrypt/archive/refs/heads/master.zip")
    dest(archiveFile)
    overwrite(false)
  }

  val unpackArchive = tasks.register<Copy>(name.replaceFirst("cinterop", "unpack")) {
    from(zipTree(archiveFile))
    into("$buildDir/libbcrypt/${konanTarget.name}")
    dependsOn(downloadArchive)
  }

  dependsOn(unpackArchive)
}
