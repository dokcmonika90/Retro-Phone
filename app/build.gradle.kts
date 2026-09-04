plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.dokcmonika90.retrophone"; compileSdk = 35
    defaultConfig { applicationId = "com.dokcmonika90.retrophone"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { viewBinding = false }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.30.5" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
