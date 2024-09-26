import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val mainClass = "no.nav.k9.los.K9LosKt"
val hikariVersion = "6.0.0"
val flywayVersion = "10.18.1"
val vaultJdbcVersion = "1.3.10"
val koinVersion = "3.5.6"
val kotliqueryVersion = "1.9.0"
val k9SakVersion = "5.0.0"
val k9KlageVersion = "0.4.5"
val fuelVersion = "2.3.1"
val jacksonVersion = "2.17.2"
val commonsTextVersion = "1.12.0"

val dusseldorfKtorVersion = "5.0.8"
val ktorVersion = "2.3.12"
val kafkaVersion = "3.8.0"

val navTilgangskontroll = "3.2024.01.24_10.14-f70bae69bd65"

// Test Dependencies
val testContainers = "1.20.1"
val jsonassertVersion = "1.5.3"
val jupiterVersion = "5.11.1"
val assertkVersion = "0.28.1"
val mockkVersion = "1.13.12"

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
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
    implementation("org.apache.httpcomponents:httpclient:4.5.14")

    // Kafka
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    // Tilgangskontroll
    implementation("no.nav.common:auth:$navTilgangskontroll")
    implementation("no.nav.common:rest:$navTilgangskontroll")
    implementation("com.google.code.gson:gson:2.11.0")

    // Kontrakter
    implementation("no.nav.k9.sak:kontrakt:$k9SakVersion")
    implementation("no.nav.k9.sak:kodeverk:$k9SakVersion")
    implementation("no.nav.k9.klage:kontrakt:$k9KlageVersion")
    implementation("no.nav.k9.klage:kodeverk:$k9KlageVersion")
    implementation("no.nav.k9.statistikk:kontrakter:2.0_20220411110858_dc06dd1")

    // opentelemetry
    implementation("io.opentelemetry:opentelemetry-api:1.42.1")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.42.1")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.8.0")

    // Div
    implementation(enforcedPlatform( "com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("org.apache.commons:commons-text:$commonsTextVersion" )
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:$fuelVersion"){
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    implementation( "io.github.smiley4:ktor-swagger-ui:3.4.0")


    // DI
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")

    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")

    testImplementation("org.testcontainers:postgresql:$testContainers")
    testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")

    testImplementation("org.apache.commons:commons-compress:1.27.1")
}

repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: "x-access-token"
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    mavenCentral()
    mavenLocal()
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    withType<ShadowJar> {
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
        mergeServiceFiles()
    }

    withType<Wrapper> {
        gradleVersion = "8.6"
    }

    withType<Test> {
        useJUnitPlatform()
        // Always run tests, even when nothing changed.
        dependsOn("cleanTest")

        // Show test results.
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
