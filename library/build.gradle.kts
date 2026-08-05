plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // Dumps the public API to api/. `checkKotlinAbi` fails when it drifts, so
    // API changes surface as a reviewable diff instead of shipping unnoticed.
    // Two dumps, because the formats differ: api/library.klib.api for the iOS
    // targets, api/jvm/library.api for the JVM one. Android needs neither —
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

    // Compose Multiplatform's own desktop artifacts are Java 11. Without a target
    // here the published jar takes the bytecode level of whichever JDK built the
    // release, and a consumer on an older one fails at class load.
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

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

        // Skiko's native library, which a desktop app loads at startup and a plain
        // unit test does not. Without it, merely constructing an AxisConfig throws:
        // its dashEffect default calls through to Skia.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
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

// Coordinates and version come from gradle.properties (GROUP, POM_ARTIFACT_ID,
// VERSION_NAME) so a release only has to bump one line.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("Grafima")
        description.set(
            "Charts for Compose Multiplatform: bar, line, pie, radar and gauge, drawn on " +
                "Canvas with animations, accessibility and RTL support on Android, iOS and desktop."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/Kyriakos-Georgiopoulos/Grafima")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("Kyriakos-Georgiopoulos")
                name.set("Kyriakos Georgiopoulos")
                url.set("https://github.com/Kyriakos-Georgiopoulos")
            }
        }

        scm {
            url.set("https://github.com/Kyriakos-Georgiopoulos/Grafima")
            connection.set("scm:git:git://github.com/Kyriakos-Georgiopoulos/Grafima.git")
            developerConnection.set("scm:git:ssh://git@github.com/Kyriakos-Georgiopoulos/Grafima.git")
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
