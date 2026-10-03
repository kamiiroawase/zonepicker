plugins {
    // Declared here with apply false so every module resolves the plugins from the shared
    // root classloader: per-module aliases gave :zonepicker and :app distinct plugin
    // classpaths once the maven-publish plugin joined only one of them, loading Spotless's
    // shared build service twice and failing task creation on Gradle 9.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.maven.publish) apply false
}
