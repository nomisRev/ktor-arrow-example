rootProject.name = "ktor-arrow-sample"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("arrow") {
            from("io.arrow-kt:arrow-version-catalog:2.2.3")
        }
    }
}
