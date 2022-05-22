plugins {
  kotlin("multiplatform")
  kotlin("native.cocoapods")
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

  cocoapods {
    version = "1.0.0"
    summary = "Kotlin MPP ...TODO"
    pod("BCryptSwift")
  }

  sourceSets {
    val commonMain by getting
    val iosArm32Main by getting
    val iosArm64Main by getting
    val iosSimulatorArm64Main by getting
    val iosX64Main by getting
    val macosArm64Main by getting
    val macosX64Main by getting
    val tvosArm64Main by getting
    val tvosSimulatorArm64Main by getting
    val tvosX64Main by getting
    val watchosArm32Main by getting
    val watchosArm64Main by getting
    val watchosSimulatorArm64Main by getting
    val watchosX64Main by getting
    val watchosX86Main by getting

    create("darwinMain") {
      dependsOn(commonMain)
      iosArm32Main.dependsOn(this)
      iosArm64Main.dependsOn(this)
      iosSimulatorArm64Main.dependsOn(this)
      iosX64Main.dependsOn(this)
      macosArm64Main.dependsOn(this)
      macosX64Main.dependsOn(this)
      tvosArm64Main.dependsOn(this)
      tvosSimulatorArm64Main.dependsOn(this)
      tvosX64Main.dependsOn(this)
      watchosArm32Main.dependsOn(this)
      watchosArm64Main.dependsOn(this)
      watchosSimulatorArm64Main.dependsOn(this)
      watchosX64Main.dependsOn(this)
      watchosX86Main.dependsOn(this)
    }

    named("jvmMain") {
      dependencies {
        implementation("at.favre.lib:bycrypt:0.9.0")
      }
    }
  }
}
