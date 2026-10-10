plugins {
    id("java-library")
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

group = "com.towerofbabel"
version = "1.0"

base {
    archivesName.set("TowerOfBabel-1.7.10")
}

// Gradle itself runs on a modern JDK; the mod is compiled and run with Java 8 like 1.7.10 expects
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

minecraft {
    mcVersion.set("1.7.10")
    username.set("Developer")
}

repositories {
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
}

tasks.processResources.configure {
    val projVersion = project.version.toString() // Needed for configuration cache to work
    inputs.property("version", projVersion)

    filesMatching("mcmod.info") {
        expand(mapOf("modVersion" to projVersion))
    }
}
