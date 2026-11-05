plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.th3s1nc.osmandhudbridge"
    compileSdk = 34
    defaultConfig {
        applicationId = "io.github.th3s1nc.hudpilot"
        minSdk = 26
        targetSdk = 34
        versionCode = 95
        versionName = "0.36.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    // Karte: MapLibre mit lokalen PMTiles-Dateien (ab 11.7.0, hier 11.8.8)
    implementation("org.maplibre.gl:android-sdk:11.8.8")
    testImplementation("junit:junit:4.13.2")
}
