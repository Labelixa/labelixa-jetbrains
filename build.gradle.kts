import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.labelixa"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Oldest platform the plugin supports (since-build 241). Building
        // against the oldest one is what keeps newer-API calls out.
        intellijIdeaCommunity("2024.1.7")
        pluginVerifier()
        zipSigner()
    }
    testImplementation(kotlin("test"))
}

// The 2024.1 platform runs on Java 17; newer bytecode would not load.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Publish guard: the plugin ships to the JetBrains Marketplace, so the
 * whole tree must be English and free of internal markers. The scanner is
 * shared with the other published packages; in the monorepo it lives in
 * `../agent/scripts/`, in the public mirror it is copied into `scripts/`.
 * Neither location present is an error, not a skip — a guard that skips
 * silently is not a guard.
 */
val checkPublishLanguage by tasks.registering(Exec::class) {
    description = "Fails when the package tree contains non-English text or internal markers."
    group = "verification"
    val root = layout.projectDirectory.asFile
    doFirst {
        val script = listOf(
            File(root, "../agent/scripts/check_publish.py"),
            File(root, "scripts/check_publish.py"),
        ).firstOrNull { it.isFile }
            ?: throw GradleException("publish guard script not found (agent/scripts/check_publish.py)")
        commandLine("python3", script.absolutePath, root.absolutePath)
    }
}
tasks.named("buildPlugin") { dependsOn(checkPublishLanguage) }
tasks.named("publishPlugin") { dependsOn(checkPublishLanguage) }
