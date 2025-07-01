import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val mainClass = "no.nav.k9.los.K9LosKt"
val hikariVersion = "6.3.0"
val flywayVersion = "11.10.0"
val vaultJdbcVersion = "1.3.10"
val koinVersion = "4.0.2"
val kotliqueryVersion = "1.9.1"
val k9SakVersion = "5.4.16"
val k9KlageVersion = "0.4.7"
val jacksonVersion = "2.19.1"
val commonsTextVersion = "1.13.1"

val dusseldorfKtorVersion = "5.0.19"
val ktorVersion = "2.3.13"
val kafkaVersion = "4.0.0"

val navTilgangskontroll = "3.2024.01.24_10.14-f70bae69bd65"

// Test Dependencies
val testContainers = "1.21.3"
val jsonassertVersion = "1.5.3"
val jupiterVersion = "5.13.2"
val assertkVersion = "0.28.1"
val mockkVersion = "1.14.4"

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0" //kan ikke gå for 2.1+ pga ktor som har avhengighet til kotlin-stdlib-common, og den er tilsynelatende ikke tilgjengelig i 2.1+
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    // Server
    implementation("no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    // Ktor
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")

    // Client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("no.nav.helse:dusseldorf-oauth2-client:$dusseldorfKtorVersion")

    // Kafka
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion") {
        //ekskluderer rocksdb siden den
        // 1. ikke i bruk gitt bruksmønsteret i los (kun les meldinger fra topic, ikke aggreggeringer osv)
        // 2. det finnes ikke noe filområde den kan skrive til
        // 3. den bidrar med vesentlig størrelse i image, siden den har binærer for mange ulike plattformer
        // dersom det blir behov for å bruke DSL store i k9-los-api, vurder InMemoryCaching
        exclude(group = "org.rocksdb", module = "rocksdbjni")
    }

    // Tilgangskontroll
    implementation("no.nav.sif.abac:kontrakt:1.4.0")

    // Kontrakter
    implementation("no.nav.k9.sak:kontrakt:$k9SakVersion")
    implementation("no.nav.k9.sak:kodeverk:$k9SakVersion")
    implementation("no.nav.k9.klage:kontrakt:$k9KlageVersion")
    implementation("no.nav.k9.klage:kodeverk:$k9KlageVersion")
    implementation("no.nav.k9.statistikk:kontrakter:2.0_20220411110858_dc06dd1")

    // opentelemetry
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.51.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.17.0")

    // Div
    implementation(enforcedPlatform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("org.apache.commons:commons-text:$commonsTextVersion")
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.1.0")


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
    testImplementation("org.testcontainers:postgresql:$testContainers")
    testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")
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
