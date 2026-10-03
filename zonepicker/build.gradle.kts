import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)
}

group = "io.github.kamiiroawase"

// Version precedence: the exact git tag (CI needs a full clone with fetch-depth=0; only
// HEAD sitting exactly on a v* tag yields that version — the release workflow's tag
// checkout is exactly this shape) > 0.0.0-SNAPSHOT (anything else: no tag at HEAD, or no
// git environment). The nearest-tag fallback is deliberately absent: past the tag it would
// stamp unreleased commits with the released version, and a local publishToMavenLocal
// would then shadow the published artifact under the same coordinates. The version must
// not be hard-coded — the tag is the single source of the released number
version =
    providers
        .of(GitTagVersionSource::class.java) {}
        .orElse("0.0.0-SNAPSHOT")
        .get()

// Configuration-cache-safe git access: the class must not reference script-level members,
// otherwise it becomes a non-static inner class
abstract class GitTagVersionSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String? {
        val process =
            try {
                // --exact-match: describe exits non-zero unless HEAD is exactly at a
                // matching tag, which is precisely the release shape
                ProcessBuilder("git", "describe", "--tags", "--exact-match", "--match=v*")
                    .redirectErrorStream(true)
                    .start()
            } catch (_: Exception) {
                return null
            }
        // Drain the output before waiting: a child whose output fills the pipe buffer
        // blocks on write and would deadlock a wait-then-read (git describe emits one
        // short line, but the shape stays correct whatever the child prints)
        val output =
            process
                .inputStream
                .bufferedReader()
                .use { it.readText() }
        // Without a tag at HEAD git describe exits non-zero and writes the error into the
        // output stream — not usable as a version
        if (process.waitFor() != 0) return null
        return output
            .trim()
            .removePrefix("v")
            .ifEmpty { null }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

android {
    namespace = "io.github.kamiiroawase.zonepicker"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    testImplementation(libs.junit)
}

// The release workflow supplies the GPG key as ORG_GRADLE_PROJECT_signingInMemoryKey and
// the plugin wires it into Gradle's signing extension by itself (the same property-based
// setup the commonmark-kotlin repo publishes with). This gate adds only two things on
// top: a blank value counts as absent — publishToMavenLocal keeps working without
// secrets, the Build workflow's artifact verification depends on that — and a non-blank
// value must be a complete ASCII-armored key, so a missing/misnamed/mangled secret fails
// with instructions instead of Gradle's cryptic "Could not read PGP secret key"
val signingKey =
    providers
        .gradleProperty("signingInMemoryKey")
        .orNull
        ?.replace("\r\n", "\n")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
if (signingKey != null) {
    require(
        signingKey.startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----") &&
            signingKey.endsWith("-----END PGP PRIVATE KEY BLOCK-----"),
    ) {
        "signingInMemoryKey is not a complete ASCII-armored PGP secret key (expected the " +
            "-----BEGIN/END PGP PRIVATE KEY BLOCK----- lines). Re-export with " +
            "'gpg --export-secret-keys --armor <key id>' and store the full output in the " +
            "ORG_GRADLE_PROJECT_signingInMemoryKey secret — a partially copied key or one " +
            "stored with literal \\n escapes fails to parse"
    }
}

// Maven Central publishing via the vanniktech plugin: the release AAR (plus sources jar,
// POM and Gradle module metadata) is signed and uploaded to the Central Portal in one
// `publishToMavenCentral` run. The release workflow supplies the credentials and the GPG
// key as environment-mapped gradle properties (ORG_GRADLE_PROJECT_mavenCentralUsername/
// Password, ORG_GRADLE_PROJECT_signingInMemoryKey/KeyPassword)
mavenPublishing {
    // automaticRelease closes and releases the staging deployment right after the upload,
    // so a green tag push needs no manual portal visit
    publishToMavenCentral(automaticRelease = true)

    if (signingKey != null) {
        signAllPublications()
    }

    pom {
        name.set("ZonePicker")
        description.set(
            "Android time zone picker: one Activity with curated grouped list, full search and follow-system option.",
        )
        url.set("https://github.com/kamiiroawase/zonepicker")
        licenses {
            license {
                name.set("The Unlicense")
                url.set("https://unlicense.org")
            }
        }
        developers {
            developer {
                id.set("kamiiroawase")
                name.set("kamiiroawase")
                url.set("https://github.com/kamiiroawase")
            }
        }
        scm {
            url.set("https://github.com/kamiiroawase/zonepicker")
            connection.set("scm:git:https://github.com/kamiiroawase/zonepicker.git")
            developerConnection.set("scm:git:git@github.com:kamiiroawase/zonepicker.git")
        }
    }
}

spotless {
    kotlin {
        target("src/*/kotlin/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("xml") {
        target("src/**/*.xml")
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}
