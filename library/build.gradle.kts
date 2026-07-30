plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // Dumps the public API to api/. `checkKotlinAbi` fails when it drifts, so
    // API changes surface as a reviewable diff instead of shipping unnoticed.
    // The dump covers the klib targets, which is the whole public surface:
    // androidMain holds no public API, only an internal `actual`.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "io.grafima.charts"
        compileSdk = 37
        minSdk = 24
        optimization {
            // Published inside the AAR so a consumer's R8 picks the rules up
            // without them having to copy anything.
            consumerKeepRules.file("consumer-rules.pro")
            consumerKeepRules.publish = true
        }
        // JVM unit tests for commonTest (pure logic + animation engines).
        withHostTest {}
        // Instrumented tests for the shared uiTest source set.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            animationsDisabled = true
        }
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

        // Compose UI tests live in src/uiTest and are compiled into BOTH test
        // targets: iOS runs them on a simulator, Android as instrumented tests.
        // Sharing the directory (rather than a dependsOn edge) keeps KMP's
        // default hierarchy template intact.
        val uiTestSrcDir = "src/uiTest/kotlin"

        iosTest {
            kotlin.srcDir(uiTestSrcDir)
            dependencies {
                implementation(libs.compose.ui.test)
            }
        }

        // `androidDeviceTest` exists but has no typed accessor in the KMP DSL.
        getByName("androidDeviceTest") {
            kotlin.srcDir(uiTestSrcDir)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.compose.ui.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.compose.ui.test.manifest)
            }
        }
    }
}

// Compose Multiplatform 1.11.1 registers a resource-copy task for the
// androidDeviceTest variant without configuring its output directory, which
// fails the build. Grafima ships no Compose resources, so the task has nothing
// to do — disable it rather than feed it a dummy directory.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }
    .configureEach { enabled = false }

// The default simulator device may not exist on a given machine; pin one and
// let CI override it via -PiosSimulatorDevice=<name>.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    device.set(providers.gradleProperty("iosSimulatorDevice").orElse("iPhone 17 Pro"))
}
