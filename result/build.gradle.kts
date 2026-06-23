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
    // Foundation phase: pure types + data structures. No coroutines dependency yet —
    // `kotlin.coroutines.cancellation.CancellationException` ships in kotlin-stdlib.
    testImplementation(kotlin("test"))
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
            }
        }
        scm {
            url.set("https://github.com/phansen314/railway")
            connection.set("scm:git:git://github.com/phansen314/railway.git")
            developerConnection.set("scm:git:ssh://git@github.com/phansen314/railway.git")
        }
    }
}
