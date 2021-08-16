import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val dusseldorfKtorVersion = "1.5.3.d73b2af"
val ktorVersion = "1.5.3"
val mainClass = "no.nav.k9.K9LosKt"
val kafkaVersion = "2.7.0" // Alligned med version fra kafka-embedded-env
val hikariVersion = "4.0.3"
val flywayVersion = "6.0.8"
val vaultJdbcVersion = "1.3.7"
val kafkaEmbeddedEnvVersion = "2.7.0"
val koinVersion = "2.2.2"
val kotliqueryVersion = "1.3.1"

plugins {
    kotlin("jvm") version "1.5.0-RC"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

dependencies {
    // Server
    implementation ( "no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")

    // Client
    implementation("no.nav.helse:dusseldorf-ktor-client:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-oauth2-client:$dusseldorfKtorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")

    // Kafka
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")


    // Tilgangskontroll
    implementation("no.nav.common:auth:2.2021.02.08_08.29-beea07de78ad")
    implementation("no.nav.common:rest:2.2021.02.08_08.29-beea07de78ad")
    implementation("com.google.code.gson:gson:2.8.6")

    // Kontrakter
    implementation("no.nav.k9.sak:kontrakt:3.2.14")
    implementation("no.nav.k9.sak:kodeverk:3.2.14")
    implementation("no.nav.k9.statistikk:kontrakter:2.0_20201201123022_bfccad8")

    // Div
    implementation(enforcedPlatform( "com.fasterxml.jackson:jackson-bom:2.12.3" ))
    implementation("info.debatty:java-string-similarity:2.0.0")
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    // DI
    implementation("org.koin:koin-core:$koinVersion")
    implementation("org.koin:koin-ktor:$koinVersion")

    // Test
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.24")
    testImplementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedEnvVersion")
    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    testImplementation("io.mockk:mockk:1.11.0")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.skyscreamer:jsonassert:1.5.0")

    testImplementation("io.zonky.test:embedded-postgres:1.3.0")
    testImplementation("org.koin:koin-test:$koinVersion")

    implementation(kotlin("stdlib-jdk8"))
    implementation("javax.ws.rs:javax.ws.rs-api:2.0")
}

repositories {
    mavenLocal()

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")

    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") // https://github.com/detekt/detekt/issues/3461
    jcenter() // https://github.com/InsertKoinIO/koin#jcenter
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
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
    gradleVersion = "7.0"
}
