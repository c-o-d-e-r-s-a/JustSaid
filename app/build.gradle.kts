// ─────────────────────────────────────────────────────────────
// JustSaid — App-level build script (Kotlin DSL)
// Local-first Android call recorder + on-device STT/LLM.
//
// WORKER-AGENT NOTE:
//   - Do NOT add any networking/analytics/crash-reporting SDK here.
//     The ONLY permitted network use is the model downloader (OkHttp) in Phase 1.
//     Any Firebase/GMS/analytics dependency is a CRITICAL security failure.
//   - Model weights are NEVER bundled. They download at runtime (see Phase 1).
// ─────────────────────────────────────────────────────────────

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.justsaid.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.justsaid.app"
        minSdk = 29            // Android 10 — scoped storage baseline for private capture buffers
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Custom runner installs HiltTestApplication so @HiltAndroidTest works (Phase 2).
        testInstrumentationRunner = "com.justsaid.app.HiltTestRunner"

        ndk {
            // ARM only — 99% of target devices. Drop x86 to keep APK small.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Vulkan stays OFF until per-device matrix testing (Phase 3 doc);
                // flip JUSTSAID_VULKAN in cpp/CMakeLists.txt when that lands.
                arguments += "-DJUSTSAID_VULKAN=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No debug symbols shipped for native libs in release.
            ndk { debugSymbolLevel = "NONE" }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Native libs are large; do not compress in APK (faster load, minor size cost).
        jniLibs { useLegacyPackaging = false }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {

    // ── Core / Kotlin ──
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Compose UI (accessibility-first, large-text, high-contrast) ──
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ── Encrypted storage: Room + SQLCipher ──
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ── Encrypted key management for the DB passphrase ──
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── DI (constructor-injected; enables test doubles for audio source, per Testing Strategy) ──
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── DataStore for simple settings (language lock, TTS notice, auto-cleanup) ──
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Model downloader ONLY. This is the single permitted network dependency. ──
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ── PDF/Text export ──
    // Uses android.graphics.pdf.PdfDocument (platform) — no external dep.

    // ── Unit tests ──
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.room:room-testing:2.6.1")
    // MockWebServer drives ModelDownloaderTest (Range resume, checksum reject, progress).
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Robolectric drives the JVM tests that need a real Android runtime:
    // SummaryDaoTest (Room), SummaryShareIntentBuilderTest (Intent), SummaryExporterTest (PdfDocument).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core-ktx:1.6.1")

    // ── Instrumented / Espresso tests ──
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    // Espresso-Intents asserts the SMS ACTION_SENDTO brokerage (SummaryActionsTest).
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(composeBom)
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.52")
    // Provides the ComponentActivity the createComposeRule tests launch.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
