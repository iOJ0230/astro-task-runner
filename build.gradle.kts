import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "2.3.11"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "com.github.ioj0230"
version = "0.0.1"

application {
    mainClass.set("com.github.ioj0230.astro.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server core + engine
    implementation("io.ktor:ktor-server-core-jvm:2.3.11")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.11")

    // JSON
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.11")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.11")

    // Logging
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.11")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Status pages (optional, for later)
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.11")

    // HttpClient (for future astro integrations)
    implementation("io.ktor:ktor-client-core-jvm:2.3.11")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:2.3.11")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.11")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:2.3.11")
}

tasks {
    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("all")
        archiveVersion.set("")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

tasks.test {
    useJUnitPlatform()
}
