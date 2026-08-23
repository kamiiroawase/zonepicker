plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.spotless)
    `maven-publish`
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

android {
    namespace = "com.github.kamiiroawase.zonepicker"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    testImplementation(libs.junit)
}

// Publication version follows the git tag when building on one. JitPack republishes
// under its own coordinates (com.github.User:Repo:Tag) regardless; this mainly
// keeps local Maven installs of non-tagged builds distinguishable ("dev").
val publicationVersion =
    providers
        .exec {
            commandLine("git", "tag", "--points-at", "HEAD")
        }.standardOutput.asText
        .get()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.removePrefix("v") ?: "dev"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.kamiiroawase"
                artifactId = "zonepicker"
                version = publicationVersion

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
                }
            }
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
        indentWithSpaces(4)
        endWithNewline()
    }
}
