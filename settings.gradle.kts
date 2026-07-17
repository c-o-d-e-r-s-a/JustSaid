// ─────────────────────────────────────────────────────────────
// JustSaid — Root settings (Kotlin DSL). See AGENTS.md §2: Kotlin DSL ONLY.
// ─────────────────────────────────────────────────────────────

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Fail if a subproject declares its own repositories — keep them centralized here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NOTE: no custom/self-hosted repos. Model weights are fetched at runtime
        // by the in-app downloader (Phase 1), never resolved as build dependencies.
    }
}

rootProject.name = "JustSaid"
include(":app")
