import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClass = "no.nav.k9.K9LosKt"
val hikariVersion = "5.0.1"
val flywayVersion = "8.5.11"
val vaultJdbcVersion = "1.3.9"
val koinVersion = "2.2.3"
val kotliqueryVersion = "1.7.0"
val k9SakVersion = "3.3.3"
val fuelVersion = "2.3.1"

val dusseldorfKtorVersion = "3.1.6.8-1a4651d"

// Disse bør henge sammen med https://github.com/navikt/dusseldorf-ktor/blob/master/pom.xml#L36
val kotlinVersion = "1.6.21"
val ktorVersion = "1.6.8"
val kafkaVersion = "3.1.0"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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
    implementation("no.nav.common:auth:2.2022.04.11_07.31-bca292df5e64")
    implementation("no.nav.common:rest:2.2022.04.11_07.31-bca292df5e64")
    implementation("com.google.code.gson:gson:2.9.0")

    // Kontrakter
    implementation("no.nav.k9.sak:kontrakt:$k9SakVersion")
    implementation("no.nav.k9.sak:kodeverk:$k9SakVersion")
    implementation("no.nav.k9.statistikk:kontrakter:2.0_20220411110858_dc06dd1")

    // Div
    implementation(enforcedPlatform( "com.fasterxml.jackson:jackson-bom:2.13.2.20220328" ))
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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
    testImplementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    testImplementation("io.mockk:mockk:1.12.4")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.skyscreamer:jsonassert:1.5.0")

    testImplementation("org.testcontainers:postgresql:1.17.1")
    testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")

    implementation(kotlin("stdlib-jdk8"))
    implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")
}

repositories {
    mavenLocal()

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

    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") // https://github.com/detekt/detekt/issues/3461
    jcenter() // https://github.com/InsertKoinIO/koin#jcenter
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
    gradleVersion = "7.4.1"
}
