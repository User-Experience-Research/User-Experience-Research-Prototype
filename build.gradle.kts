plugins {
    kotlin("jvm") version "2.2.20"
    application
    id("io.ktor.plugin") version "3.5.0"
}

group = "org.nmsi"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.0")
    implementation("io.ktor:ktor-server-config-yaml-jvm:3.5.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.0")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.0")
    implementation("io.ktor:ktor-server-pebble-jvm:3.5.0")
    implementation("com.zaxxer:HikariCP:6.3.2")
    implementation("org.flywaydb:flyway-core:11.13.2")
    implementation("org.flywaydb:flyway-database-postgresql:11.13.2")
    implementation("org.postgresql:postgresql:42.7.8")
    runtimeOnly("com.h2database:h2:2.3.232")

    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.0")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
