plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dokcmonika90.retrophone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dokcmonika90.retrophone"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        externalNativeBuild { cmake { cppFlags += "-O3 -std=c++20" } }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.30.5" }
    }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
