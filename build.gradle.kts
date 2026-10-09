import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    // Declared here with apply false so every module resolves the plugins from the shared
    // root classloader: per-module aliases gave :zonepicker and :app distinct plugin
    // classpaths once the maven-publish plugin joined only one of them, loading Spotless's
    // shared build service twice and failing task creation on Gradle 9.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false

    // Spotless applies at the root too: the per-module "*.gradle.kts" targets never reach
    // the root's build.gradle.kts and settings.gradle.kts. base supplies this project's
    // assemble/check/build lifecycle so CI's plain `gradlew build` also runs the root
    // spotlessCheck alongside the module builds.
    base
    alias(libs.plugins.spotless)
}

// Spotless wires spotlessCheck into `check` itself when the task exists; the explicit edge
// keeps that true regardless of plugin application order.
tasks.named("check") { dependsOn(tasks.named("spotlessCheck")) }

// One shared rule set for every project applying the plugin — this root plus :zonepicker and
// :app, which still apply it themselves in their plugins blocks. The per-module blocks this
// replaces were three near-identical copies that could drift apart; a target matching no
// sources in a project (the root has no src/) is simply a no-op there.
allprojects {
    pluginManager.withPlugin("com.diffplug.spotless") {
        configure<SpotlessExtension> {
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
    }
}
