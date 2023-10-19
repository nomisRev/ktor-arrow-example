plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

@Suppress("GradlePluginVersion")
dependencies {
    compileOnly(gradleKotlinDsl())
    implementation(libs.kotlin.gradle)
    implementation(libs.detekt.gradle)
}

kotlin {
    jvmToolchain(19)
}