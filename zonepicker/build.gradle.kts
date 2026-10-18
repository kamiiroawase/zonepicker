import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)

    // API reference from the KDoc: ./gradlew :zonepicker:dokkaGeneratePublicationHtml
    // (CI uploads the result as an artifact); configuration is Dokka's Android defaults
    alias(libs.plugins.dokka)
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
        compose = true
    }
}

// binary-compatibility-validator registers no tasks under AGP 9's built-in Kotlin (no KGP
// compilation exists for it to hook), so the API guard is a local equivalent: javap -public
// over the release AAR's classes dumps the public binary surface, apiCheck — wired into
// `check`, hence `build` and CI — diffs it against the committed golden api/zonepicker.api
// and fails on drift, so accidental API breaks surface at PR time instead of in a
// consumer's build. Swap for the real plugin whenever it learns built-in Kotlin.
//
// javap cannot see Kotlin visibility (internal compiles to public bytecode), so the surface
// lists the library's public types explicitly: everything else in classes.jar is internal
// by definition. Adding a public type means adding it here and regenerating — a deliberate
// act, which is the point.
val publicApiClasses =
    setOf(
        "io.github.kamiiroawase.zonepicker.ZonePicker",
        "io.github.kamiiroawase.zonepicker.ZonePickerActivity",
        "io.github.kamiiroawase.zonepicker.ZonePickerContract",
        "io.github.kamiiroawase.zonepicker.ZonePickerDialog",
        "io.github.kamiiroawase.zonepicker.ZonePickerBottomSheet",
        "io.github.kamiiroawase.zonepicker.ZonePickerRequest",
        "io.github.kamiiroawase.zonepicker.ZonePickerResult",
    )

val releaseAar = layout.buildDirectory.file("outputs/aar/zonepicker-release.aar")

val apiGoldenFile = layout.projectDirectory.file("api/zonepicker.api")

/** Kotlin's default-parameter trampolines, Compose's update skips, stability fields and
 *  synthetic accessors — generated, not API. (The dollar signs go through character classes —
 *  the Gradle Kotlin DSL restricts bare ones.) */
private val generatedMemberPattern = Regex("""[$]default\(|[$]update\(|[$]updateUiState\(|int [$]stable;|access[$]""")

// The javap pipeline both API tasks share lives in this nested object, not in a plain
// helper function: a function declared in a build script is a member of the script class,
// so a task action calling it captures the script instance, which the configuration cache
// rejects — while a nested object is a static reference the task action may hold freely.
object ApiGuard {
    /** The public binary surface of the release AAR as javap text, filtered of compiler
     *  synthetics and restricted to the allowlisted public classes. */
    fun surface(
        aarFile: File,
        classes: Set<String>,
        noise: Regex,
        workDir: File,
    ): String {
        val classesJar = workDir.resolve("classes.jar")

        workDir.mkdirs()
        ZipFile(aarFile).use { zip ->
            zip.getInputStream(zip.getEntry("classes.jar")).use { input -> classesJar.outputStream().use { input.copyTo(it) } }
        }

        fun isPublicApi(className: String): Boolean =
            classes.any { listed ->
                val isSelf = className == listed || className == listed + "Kt"
                val nested = className.removePrefix(listed + '$')

                isSelf || (nested != className && nested.split('$').all { it.isNotEmpty() && it.first().isUpperCase() })
            }

        val classNames =
            ZipFile(classesJar).use { jar ->
                jar
                    .entries()
                    .asSequence()
                    .filter { it.name.endsWith(".class") }
                    .map { it.name.removeSuffix(".class").replace('/', '.') }
                    .filter(::isPublicApi)
                    .sorted()
                    .toList()
            }

        val javap =
            ProcessBuilder(
                listOf("javap", "-public", "-classpath", classesJar.absolutePath) + classNames,
            ).redirectErrorStream(true).start()

        val output = javap.inputStream.bufferedReader().use { it.readText() }

        check(javap.waitFor() == 0) { "javap failed:\n$output" }

        val surface = StringBuilder()
        var keepingClass = false

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trimEnd()

            when {
                line.isEmpty() || line.startsWith("Compiled from") -> {
                    Unit
                }

                line.endsWith("{") -> {
                    keepingClass = line.startsWith("public ")

                    if (keepingClass) surface.appendLine(line)
                }

                line == "}" -> {
                    if (keepingClass) surface.appendLine(line)

                    keepingClass = false
                }

                keepingClass && !noise.containsMatchIn(line) -> {
                    surface.appendLine(line)
                }
            }
        }

        return surface.toString()
    }
}

tasks.register("apiDump") {
    group = "verification"
    description = "Regenerates api/zonepicker.api from the release AAR."
    dependsOn("assembleRelease")

    val aarFile = releaseAar
    val goldenFile = apiGoldenFile
    val classes = publicApiClasses
    val noise = generatedMemberPattern
    inputs.file(aarFile)
    outputs.file(goldenFile)

    doLast {
        goldenFile.asFile.writeText(ApiGuard.surface(aarFile.get().asFile, classes, noise, temporaryDir))
    }
}

tasks.register("apiCheck") {
    group = "verification"
    description = "Fails when the public API drifted from api/zonepicker.api."
    dependsOn("assembleRelease")

    val aarFile = releaseAar
    val goldenFile = apiGoldenFile
    val classes = publicApiClasses
    val noise = generatedMemberPattern
    inputs.file(aarFile)
    inputs.file(goldenFile)

    doLast {
        val surface = ApiGuard.surface(aarFile.get().asFile, classes, noise, temporaryDir)
        val expected = goldenFile.asFile.readText()

        check(surface == expected) {
            "The public API drifted from ${goldenFile.asFile.name}; run ./gradlew :zonepicker:apiDump, review the diff and commit it (or change the code)"
        }
    }
}

// The same wiring the validator plugin would install: every `check` — and therefore `build`
// and CI — verifies the golden file against the current surface
tasks.named("check") { dependsOn(tasks.named("apiCheck")) }

dependencies {
    // api, not implementation: ZonePickerContract's signature exposes ActivityResultContract
    // and the Compose entries expose Color/Modifier, which must reach consumers' compile
    // classpath
    api(libs.androidx.activity)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
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
