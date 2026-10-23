plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktfmtVersion = libs.versions.ktfmt.get()
    kotlin {
        target("app/src/**/*.kt")
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
}
