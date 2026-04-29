plugins {
    // Toolchain pinned to Reown AppKit 1.6.12's tested combo (sample dapp
    // in github.com/reown-com/reown-kotlin). Bumping Kotlin from 1.9.22 to
    // 2.2.10 was forced by Reown's transitive deps shipping kotlin-stdlib
    // 2.2.10 — the 1.9 compiler can't read metadata version 2.2.
    //
    // With Kotlin 2.0+ the Compose Compiler is a dedicated plugin
    // ("org.jetbrains.kotlin.plugin.compose") instead of the old
    // composeOptions.kotlinCompilerExtensionVersion knob.
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
