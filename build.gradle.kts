import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClass = "no.nav.k9.los.K9LosKt"
val hikariVersion = "5.0.1"
val flywayVersion = "9.9.0"
val vaultJdbcVersion = "1.3.10"
val koinVersion = "3.3.0"
val kotliqueryVersion = "1.9.0"
val k9SakVersion = "3.3.25"
val fuelVersion = "2.3.1"
val jacksonVersion = "2.13.4"

val dusseldorfKtorVersion = "3.2.2.1-4942135"
val ktorVersion = "2.2.1"
val kafkaVersion = "3.3.1"

val navTilgangskontroll = "2.2022.11.16_08.36-35c94368bc44"

// Test Dependencies
val testContainers = "1.17.6"
val jsonassertVersion = "1.5.1"
val jupiterVersion = "5.9.1"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.22"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    // Server
    implementation ("no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    implementation ("no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    implementation ("no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    implementation ("no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    implementation ("no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    // Ktor
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-locations-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")

    // Client
    implementation("no.nav.helse:dusseldorf-ktor-client:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-oauth2-client:$dusseldorfKtorVersion")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")

    // Kafka
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    // Tilgangskontroll
    implementation("no.nav.common:auth:$navTilgangskontroll")
    implementation("no.nav.common:rest:$navTilgangskontroll")
    implementation("com.google.code.gson:gson:2.10")

    // Kontrakter
    implementation("no.nav.k9.sak:kontrakt:$k9SakVersion")
    implementation("no.nav.k9.sak:kodeverk:$k9SakVersion")
    implementation("no.nav.k9.statistikk:kontrakter:2.0_20220411110858_dc06dd1")

    // Div
    implementation(enforcedPlatform( "com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("info.debatty:java-string-similarity:2.0.0")
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:$fuelVersion"){
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    // DI
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")

    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testImplementation("io.mockk:mockk:1.13.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")

    testImplementation("org.testcontainers:postgresql:$testContainers")
    testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")
}

repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_PASSWORD")
        }
    }

    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")

    mavenLocal()
}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("app")
    archiveClassifier.set("")
    isZip64 = true
    manifest {
        attributes(
            mapOf(
                "Main-Class" to mainClass
            )
        )
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "7.6"
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Always run tests, even when nothing changed.
    dependsOn("cleanTest")

    // Show test results.
    testLogging {
        events("passed", "skipped", "failed")
    }
}
