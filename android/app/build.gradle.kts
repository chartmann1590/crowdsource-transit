import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.firebase-perf")
    id("com.google.firebase.crashlytics")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

android {
    namespace = "com.charles.crowdtransit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.charles.crowdtransit.app"
        minSdk = 24
        targetSdk = 35
        // Version precedence: ANDROID_VERSION_CODE/ANDROID_VERSION_NAME env vars first
        // (used by the Play publish workflow for monotonic UTC timestamps), then CI's
        // -PappVersionCode/-PappVersionName (GitHub Actions run number), then local default.
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull()
            ?: (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
            ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME")
            ?: (project.findProperty("appVersionName") as String?)
            ?: "1.0.0"
        buildConfigField(
            "String",
            "TRANSITLAND_API_KEY",
            "\"${localProperties.getProperty("transitland.apiKey", "")}\"",
        )
        buildConfigField(
            "String",
            "GITHUB_API_TOKEN",
            "\"${localProperties.getProperty("github.api.token", "")}\"",
        )
        buildConfigField(
            "String",
            "GITHUB_REPO_OWNER",
            "\"${localProperties.getProperty("github.repo.owner", "")}\"",
        )
        buildConfigField(
            "String",
            "GITHUB_REPO_NAME",
            "\"${localProperties.getProperty("github.repo.name", "")}\"",
        )
        buildConfigField(
            "String",
            "FEEDBACK_ASSETS_DIR",
            "\"feedback-assets\"",
        )

        // Google's official AdMob test IDs (safe to use/hardcode; never serve real ads).
        // Used as the default everywhere except the release build type, which pulls the
        // real production IDs from local.properties (populated from GitHub secrets in CI).
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProperties.getProperty("release.storeFile", "release.keystore.jks"))
            storePassword = localProperties.getProperty("release.storePassword", "")
            keyAlias = localProperties.getProperty("release.keyAlias", "")
            keyPassword = localProperties.getProperty("release.keyPassword", "")
        }
    }

    buildTypes {
        debug {
            // Test ad IDs from defaultConfig apply as-is.
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = localProperties.getProperty(
                "admob.appId", "ca-app-pub-3940256099942544~3347511713",
            )
            buildConfigField(
                "String", "ADMOB_BANNER_AD_UNIT_ID",
                "\"${localProperties.getProperty("admob.bannerId", "ca-app-pub-3940256099942544/6300978111")}\"",
            )
            buildConfigField(
                "String", "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "\"${localProperties.getProperty("admob.interstitialId", "ca-app-pub-3940256099942544/1033173712")}\"",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Works around a lint-tooling crash (not a real issue in our code): this AGP/Kotlin
        // analysis API version combo throws "Found class ...KaCallableMemberCall, but
        // interface was expected" inside NonNullableMutableLiveDataDetector, which otherwise
        // fails lintVitalAnalyzeRelease and blocks every release build.
        disable += "NullSafeMutableLiveData"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }

}


dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")

    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    implementation("org.maplibre.gl:android-sdk:11.8.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Force 16 KB-aligned version; the Compose BOM pulls in an older build via
    // androidx.graphics:graphics-core that contains a misaligned libandroidx.graphics.path.so
    implementation("androidx.graphics:graphics-path:1.0.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
