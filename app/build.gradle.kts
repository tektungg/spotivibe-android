import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Read Spotify Client ID from local.properties (gitignored)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val spotifyClientId: String = localProps.getProperty("spotify.clientId", "")

// Read release signing config dari keystore.properties (gitignored).
// Kalau file tidak ada (mis. CI atau fresh clone), release build skip sign
// dan output unsigned APK — bisa di-sign manual dengan apksigner.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.tglabs.spotivibe"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tglabs.spotivibe"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Spotify configuration exposed via BuildConfig
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"spotivibe://callback\"")

        // Manifest placeholder for Spotify auth intent filter
        manifestPlaceholders["redirectSchemeName"] = "spotivibe"
        manifestPlaceholders["redirectHostName"] = "callback"
    }

    /**
     * Dua varian, dibedakan HANYA oleh dukungan Jepang.
     *
     * Kamus IPADIC milik kuromoji mengisi 12,71 dari 15,75 MB APK, yaitu 81%
     * ukurannya untuk satu bahasa. Kanji tidak bisa diromanisasi tanpa kamus
     * morfologis, jadi tidak ada versi ringan dari kemampuan itu: pilihannya
     * memuat kamusnya atau tidak mendukung Jepang.
     *
     * Play Feature Delivery tidak dipakai karena distribusinya lewat GitHub
     * Releases, bukan Play Store, dan modul on-demand butuh Play.
     *
     * applicationId keduanya SAMA, jadi lite menimpa full dan sebaliknya. Itu
     * disengaja: dua app dengan nama sama yang terpasang berbarengan akan
     * berebut redirect auth `spotivibe://callback`, dan itu persis bug yang
     * pernah membuat login tidak bisa selesai.
     */
    flavorDimensions += "romanization"
    productFlavors {
        create("full") {
            dimension = "romanization"
            isDefault = true
        }
        create("lite") {
            dimension = "romanization"
            versionNameSuffix = "-lite"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Pakai signing config kalau keystore.properties ada
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 minify + resource shrinking — strip unused code, obfuscate names.
            // Keep rules untuk Spotify SDK, kuromoji, pinyin4j, Moshi, Retrofit
            // sudah di proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // android.util.Log punya implementasi stub di JVM yang melempar exception.
    // Gate test kita murni logika (PKCE, parsing redirect, expiry token,
    // capability) tapi kelas yang diuji ikut menulis log, jadi stub-nya
    // dibikin mengembalikan default alih-alih melempar.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Resolve duplicate META-INF files dari multiple JARs (kuromoji-ipadic +
    // kuromoji-core sama-sama include CONTRIBUTORS.md, dll). Files ini hanya
    // metadata docs, tidak dibutuhkan saat runtime.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.md",
                "META-INF/*.markdown",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }
}

dependencies {
    // Compose UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)

    // Lifecycle + ViewModel + Compose state
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Palette API — extract dominant color from album art bitmap
    implementation(libs.androidx.palette.ktx)

    // Networking — LRCLIB API
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Romanization — JA (kuromoji), ZH (pinyin4j), KO (manual port di RomanizationService)
    // kuromoji HANYA di varian full. Ini satu-satunya baris yang membuat APK
    // lite turun dari 15,75 MB jadi sekitar 3 MB.
    "fullImplementation"(libs.kuromoji.ipadic)
    // pinyin4j ada di KEDUA varian: cuma 0,21 MB, tidak layak dibuang.
    implementation(libs.pinyin4j)

    // DataStore — persist toggle preferences
    implementation(libs.androidx.datastore.preferences)

    // MediaStyle notification — lock screen + pull-down dengan synced lyric line
    implementation(libs.androidx.media)

    // Custom Tabs — layar consent OAuth (Authorization Code + PKCE)
    implementation(libs.androidx.browser)

    // Spotify Android SDK (distributed via AAR files in libs/, not Maven Central)
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(files("libs/spotify-auth-release-2.1.0.aar"))
    // Gson required at runtime by Spotify App Remote SDK
    implementation("com.google.code.gson:gson:2.10.1")

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
