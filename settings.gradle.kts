rootProject.name = "TowerOfBabel"

pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
                includeGroup("com.gtnewhorizons")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Automatically downloads a Java 8 JDK if one isn't installed
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
