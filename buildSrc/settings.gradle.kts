// buildSrc is a separate build: it inherits neither the root's repositories nor
// its version catalog, so both are declared again here rather than pinning a
// second copy of the ktlint version.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
