// ─────────────────────────────────────────────────────────────
// JustSaid — Root build script (Kotlin DSL). See AGENTS.md §2: Kotlin DSL ONLY.
// Declares plugin versions once; subprojects apply them without a version.
// Version bumps happen HERE and nowhere else.
// ─────────────────────────────────────────────────────────────

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Compose compiler plugin version MUST match the Kotlin version above.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
