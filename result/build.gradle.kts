import com.vanniktech.maven.publish.SonatypeHost

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // API documentation (HTML) with compiled `@sample` snippets.
    alias(libs.plugins.dokka)
    // Maven Central publication (sources + javadoc jars, POM, signing, Central Portal upload).
    alias(libs.plugins.mavenPublish)
}

kotlin {
    // Strict explicit-API: every public declaration must state its visibility and
    // return type. Keeps the public surface complete and predictable for consumers
    // (and AI agents) and prevents accidental public leakage. Scoped to :result only.
    explicitApi()

    // Compile the KDoc `@sample` snippets as part of the test compilation so they are
    // type-checked, without polluting the real test suite.
    sourceSets["test"].kotlin.srcDir("src/samples/kotlin")
}

dokka {
    dokkaSourceSets.named("main") {
        // Source of the functions referenced by `@sample` tags in KDoc.
        samples.from("src/samples/kotlin")
    }
}

dependencies {
    // Pure types + data structures — zero runtime dependencies. Suspend support is
    // inline-from-suspend (the combinators and `rail { }` are inline, so suspend lambdas
    // work from a suspend caller) and `CancellationException` ships in kotlin-stdlib, so
    // coroutines is needed only to *drive* the suspend test suite — never at runtime.
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesCore)
    testImplementation(libs.kotlinxCoroutinesTest)
}

// Coordinates (GROUP / POM_ARTIFACT_ID / VERSION_NAME) come from gradle.properties.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    pom {
        name.set("railway")
        description.set("A zero-allocation, three-rail railway-oriented result type for Kotlin/JVM.")
        url.set("https://github.com/phansen314/railway")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("phansen314")
                name.set("phansen")
                email.set("codingzen314@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/phansen314/railway")
            connection.set("scm:git:git://github.com/phansen314/railway.git")
            developerConnection.set("scm:git:ssh://git@github.com/phansen314/railway.git")
        }
    }
}
