plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "io.grafima.charts"
        compileSdk = 37
        minSdk = 24
        // JVM unit tests for commonTest (pure logic + animation engines).
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Compose types appear in Grafima's public API -> exposed via `api`.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.animation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Compose UI tests run on the iOS simulator only: on Android they would
        // need instrumentation, which is deliberately out of scope for now.
        iosTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}

// The default simulator device may not exist on a given machine; pin one and
// let CI override it via -PiosSimulatorDevice=<name>.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    device.set(providers.gradleProperty("iosSimulatorDevice").orElse("iPhone 17 Pro"))
}
