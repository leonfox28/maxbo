import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}

// Release signing reads key.properties at the root (gitignored). Without it,
// release builds fall back to the debug key so anyone can build locally.
val keyProperties =
    Properties().apply {
        val file = rootProject.file("key.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

android {
    namespace = "io.github.leonfox28.maxbo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.leonfox28.maxbo"
        minSdk = 24
        targetSdk = 36
        // One more for each release. The Flutter builds before 0.12.0 went up
        // to 4021 (ABI × 1000 + build number), so this installs over them.
        versionCode = 5025
        versionName = "0.12.3"
        manifestPlaceholders["appLabel"] = "maxbo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keyProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds install next to the release app, so a signature
        // mismatch never forces an uninstall (and the login state with it).
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "maxbo debug"
        }
        release {
            signingConfig =
                signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // A release build installable over the debug app and its login, to
        // measure smoothness (debug builds are far slower).
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "maxbo debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        // java.time on Android 7.
        isCoreLibraryDesugaringEnabled = true
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

// Whole stack traces in the log: CI keeps no test reports to look at.
tasks.withType<Test>().configureEach {
    testLogging { exceptionFormat = TestExceptionFormat.FULL }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.telephoto.flick)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)

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
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.coil.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
