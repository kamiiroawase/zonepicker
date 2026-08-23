plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotless)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

android {
    namespace = "com.github.kamiiroawase.zonepicker.demo"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.kamiiroawase.zonepicker.demo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":zonepicker"))
    implementation(libs.material)
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
