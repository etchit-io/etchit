pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Patched com.reown:android-core AAR (ChaCha20Poly1305 thread-safety
        // fix). Source: https://github.com/etchit-io/reown-kotlin tag
        // 1.6.12-etchit.1. See local-maven/README.md for details.
        maven { url = uri("$rootDir/local-maven") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "etchit"
include(":app")
