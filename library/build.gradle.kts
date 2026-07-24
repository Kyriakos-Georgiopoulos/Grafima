plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "io.grafima.charts"
        compileSdk = 37
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Compose types appear in Grafima's public API -> exposed via `api`.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.animation)
        }
    }
}
