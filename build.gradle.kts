plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    application
}

group = "com.geofencing"
version = "1.0.0"

application {
    mainClass.set("com.geofencing.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val logbackVersion = "1.4.14"
val hikariVersion = "5.1.0"
val postgresVersion = "42.7.3"
val dotenvVersion = "6.4.1"

dependencies {
    // Ktor Core & Netty Engine
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")

    // Content Negotiation & JSON Serialization
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    // Authentication & JWT
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")

    // CORS & Status Pages
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // Database & Connection Pooling
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    // Environment Variables
    implementation("io.github.cdimascio:dotenv-kotlin:$dotenvVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Unit Testing
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test-junit"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
