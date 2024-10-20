
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.0.20"
    id("io.ktor.plugin") version "3.0.0-rc-2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-default-headers-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.2.0")
    implementation("org.mongodb:bson-kotlinx:5.2.0")
    implementation("at.favre.lib:bcrypt:0.9.0")
    implementation("org.litote.kmongo:kmongo-coroutine:4.4.0")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.0-rc-2")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("io.ktor:ktor-server-call-logging:2.0.0")
        implementation("io.ktor:ktor-server-core:2.3.0")
        implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("io.ktor:ktor-server-websockets:2.x.x")  // Replace 2.x.x with the latest version

        implementation("org.litote.kmongo:kmongo-coroutine:4.3.0")   // Use the latest version of KMongo

implementation("ch.qos.logback:logback-classic:$logback_version")





}
