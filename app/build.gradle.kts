plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    //id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // KSP was only for Room, so we can remove it now
}

android {
    namespace = "com.nick.nutritiontracker"
    // Note: compileSdk 36 is very experimental. 34 or 35 is recommended for stability.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nick.nutritiontracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.1"

        // ROOM KSP BLOCK REMOVED FROM HERE
    }

    buildFeatures {
        compose = true
    }

    // ADD THIS BLOCK:
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10" // Compatible version for Kotlin 1.9.22
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Utilities
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Local Storage (JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ROOM DEPENDENCIES REMOVED
}
