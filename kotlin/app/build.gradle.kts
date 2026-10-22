plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.leonfox28.maxbo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.leonfox28.maxbo"
        minSdk = 24
        targetSdk = 36
        // Above every Flutter build (ABI × 1000 + build number, up to 4021
        // for v0.11.0), so the Kotlin app installs over them.
        versionCode = 5022
        versionName = "0.12.0-dev"
        manifestPlaceholders["appLabel"] = "maxbo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // `.dev` until the switch (KOTLIN_MIGRATION.md §2.2), so the Flutter
        // debug app and its login stay as they are.
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "maxbo dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // A release build installable over the `.dev` app and its login, to
        // measure smoothness (debug builds are far slower).
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "maxbo dev"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    lint {
        // targetSdk stays at 36 until the app has been tried on 37, and the
        // app ships as an APK, never split by language.
        disable += listOf("OldTargetApi", "AppBundleLocaleChanges")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Plain JVM tests may reach android.util.Log through devLog().
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
