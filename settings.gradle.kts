rootProject.name = "ktor-arrow-sample"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("arrow") {
            from("io.arrow-kt:arrow-stack:2.1.2")
        }
    }
}
