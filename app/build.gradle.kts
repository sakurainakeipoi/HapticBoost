import java.util.Properties

plugins {
    id("com.android.application")
}

val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: keystoreProperties.getProperty(name)?.takeIf(String::isNotBlank)

val releaseSigningValues = mapOf(
    "KEYSTORE_PATH" to signingValue("KEYSTORE_PATH"),
    "KEYSTORE_PASSWORD" to signingValue("KEYSTORE_PASSWORD"),
    "KEY_ALIAS" to signingValue("KEY_ALIAS"),
    "KEY_PASSWORD" to signingValue("KEY_PASSWORD"),
)
val hasReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "io.github.sakurainakeipoi.hapticboost"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.sakurainakeipoi.hapticboost"
        minSdk = 21
        targetSdk = 34
        versionCode = 6
        versionName = "1.3.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningValues.getValue("KEYSTORE_PATH")!!)
                storePassword = releaseSigningValues.getValue("KEYSTORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
