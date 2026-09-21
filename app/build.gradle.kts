plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.sakurainakeipoi.hapticboost"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.sakurainakeipoi.hapticboost"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
}

