import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.charles.crowdtransit.app"
        // API 26: the navigation foreground service requires startForegroundService +
        // notification channels (mandatory since Android 8). Below 26 those calls crash, so
        // 24–25 were never actually functional for navigation.
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Version precedence: ANDROID_VERSION_CODE/ANDROID_VERSION_NAME env vars first
        // (used by the Play publish workflow for monotonic UTC timestamps), then CI's
        // -PappVersionCode/-PappVersionName (GitHub Actions run number), then local default.
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull()
            ?: (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
            ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME")
            ?: (project.findProperty("appVersionName") as String?)
            ?: "1.0.0"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
        buildConfigField(
            "String",
            "TRANSITLAND_API_KEY",
            "\"${localProperties.getProperty("transitland.apiKey", "")}\"",
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

    // Ships only the device's ABI in the Play bundle. The LiteRT-LM runtime (Hopper AI
    // assistant, gated to API 31+) carries native libs for every ABI; without this every
    // install — including devices that can never run the assistant — pays for all of them.
    bundle {
        abi {
            enableSplit = true
        }
    }

    lint {
        // Works around a lint-tooling crash (not a real issue in our code): this AGP/Kotlin
        // analysis API version combo throws "Found class ...KaCallableMemberCall, but
        // interface was expected" inside NonNullableMutableLiveDataDetector, which otherwise
        // fails lintVitalAnalyzeRelease and blocks every release build.
        disable += "NullSafeMutableLiveData"
    }

    compileOptions {
        // Bumped from 17: the Hopper AI assistant's litertlm-android dependency ships
        // class files compiled to Java 21 bytecode (major version 65); kapt's javac stub
        // pass can't read those under a 17 toolchain ("bad class file ... should be 61.0").
        // This only changes the JVM level used to *compile* app code — minSdk (26),
        // targetSdk, and compileSdk are untouched, and D8/R8 still desugar down to what
        // the device's runtime needs regardless of source/target level.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(21)
    }

    sourceSets {
        getByName("test") {
            // Golden itinerary fixtures shared with the web tests (docs/routing/itinerary-spec.md)
            resources.srcDir(rootProject.file("../docs/routing/fixtures"))
        }
        getByName("androidTest") {
            // Exported Room schema history, consumed by MigrationTestHelper.
            assets.srcDir(file("schemas"))
        }
    }

}


configurations.all {
    resolutionStrategy {
        // The Compose BOM (added to androidTest for ui-test-junit4) strictly pins
        // kotlinx-serialization to 1.7.3, but androidx.room:room-testing's
        // room-migration-jvm needs 1.8.1's GeneratedSerializer ABI (AbstractMethodError
        // otherwise). Force the newer, ABI-compatible version everywhere.
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
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
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("com.google.dagger:hilt-android:2.58")
    kapt("com.google.dagger:hilt-android-compiler:2.58")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")

    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
    // "Remove Ads" subscription — see data/billing/BillingRepository.kt.
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    implementation("org.maplibre.gl:android-sdk:11.8.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Bumped 1.9.0 -> 1.11.0 alongside litertlm-android 0.15.0 (see gradle/libs.versions.toml):
    // 0.15.0's Conversation.sendMessageAsync completion callback calls a SendChannel.close
    // overload that doesn't exist in 1.9.0, confirmed via a live-device crash
    // (NoSuchMethodError: close$default) that fired right after a real multimodal reply
    // had already finished streaming.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // KSP, not kapt: moshi-kotlin-codegen's kapt path doesn't support Kotlin 2.3.0
    // metadata (see gradle/libs.versions.toml for why Kotlin was bumped to 2.3.0).
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Hopper on-device AI assistant (Gemma 4 E2B). Requires API 31+ at runtime — this app's
    // minSdk stays 26, so every reference to this library is confined to
    // app/ai/engine/LiteRtAssistantEngine.kt and TransitToolSet.kt, loaded only behind an
    // SDK_INT check (see AssistantEngineFactory). Pinned exact version — never latest.release.
    // Pinned to 0.9.0-beta specifically for Kotlin-metadata compatibility (see
    // gradle/libs.versions.toml). It has a real native-library-loading race — "No
    // implementation found for NativeLibraryLoader.nativeCheckLoaded()" — confirmed on real
    // hardware; worked around in LiteRtAssistantEngine.tryInitialize() with an explicit
    // pre-load + retry rather than by chasing a newer library version (see that file's
    // comment for why the version-bump path is a dead end without a Moshi/KSP migration).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.15.0")

    // Force 16 KB-aligned version; the Compose BOM pulls in an older build via
    // androidx.graphics:graphics-core that contains a misaligned libandroidx.graphics.path.so
    implementation("androidx.graphics:graphics-path:1.1.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
