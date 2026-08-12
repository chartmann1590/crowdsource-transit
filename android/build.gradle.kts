plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Declared here (apply false), not just in app/build.gradle.kts: the Hilt Gradle
    // plugin fails to detect KSP if the two are applied from different classloader
    // scopes (root vs. subproject) — https://github.com/google/dagger/issues/3965.
    alias(libs.plugins.ksp) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.3" apply false
    id("com.google.firebase.firebase-perf") version "2.0.2" apply false
    id("com.google.dagger.hilt.android") version "2.58" apply false
}
