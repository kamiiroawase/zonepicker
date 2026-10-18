plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

android {
    namespace = "io.github.kamiiroawase.zonepicker.demo"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.kamiiroawase.zonepicker.demo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Release-APK signing, driven entirely by env vars: the Release workflow decodes the
    // KEYSTORE_BASE64 secret to signing.keystore and passes it plus the credentials (the
    // same contract the kamiiroawase APK repos use). Absent vars leave the release build
    // unsigned — local builds and the Build workflow on main need no secrets; a partial
    // set fails loudly instead of producing an APK Gradle silently left unsigned
    val keystoreFile = providers.environmentVariable("KEYSTORE_FILE").orNull

    if (keystoreFile != null) {
        val storePass = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
        val alias = providers.environmentVariable("KEY_ALIAS").orNull
        val keyPass = providers.environmentVariable("KEY_PASSWORD").orNull

        require(storePass != null && alias != null && keyPass != null) {
            "KEYSTORE_FILE is set but the signing env is incomplete (need KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) — the release APK cannot be signed"
        }

        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }

        buildTypes {
            release {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":zonepicker"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
