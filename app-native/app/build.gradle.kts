import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.altomedia.beruang"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.altomedia.beruang"
        minSdk = 23
        targetSdk = 37
        versionCode = 6
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Reuses the existing ALTOMEDIA keystore (see ALTOMEDIA/keystore.properties).
            // For local builds the file may be absent; release signing is then a no-op.
            val ksFile = rootProject.file("../ALTOMEDIA/keystore.properties")
            if (ksFile.exists()) {
                val props = Properties().apply { ksFile.inputStream().use { load(it) } }
                storeFile = rootProject.file("../" + props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Coil — image loading (avatars, post images)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Supabase Kotlin client + Ktor engine
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.0")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.0.0")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.0.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.0.0")
    implementation("io.ktor:ktor-client-android:3.0.0")

    // Kotlinx serialization + coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pin transitive Kotlin/Ktor libs to versions compatible with Kotlin 2.0.21
    constraints {
        implementation("io.ktor:ktor-client-core:3.0.0")
        implementation("io.ktor:ktor-http:3.0.0")
        implementation("io.ktor:ktor-utils:3.0.0")
        implementation("io.ktor:ktor-io:3.0.0")
        implementation("io.ktor:ktor-serialization:3.0.0")
        implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
        implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
        implementation("io.ktor:ktor-client-websockets:3.0.0")
        implementation("io.ktor:ktor-client-logging:3.0.0")
        implementation("io.ktor:ktor-events:3.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.5.4")
        implementation("org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.5.4")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    }

    // ZXing — QR code generation + scanning
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Google Mobile Ads (AdMob: banner / interstitial / rewarded)
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // DataStore for local persistence (session/prefs)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
