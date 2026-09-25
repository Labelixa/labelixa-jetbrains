rootProject.name = "labelixa-zpl"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// The 2024.1 platform needs a Java 17 toolchain. With this resolver Gradle
// downloads one (Adoptium) when the machine has none, so a contributor with
// only a newer JDK can still build.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
