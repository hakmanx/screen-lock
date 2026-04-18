import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciKeystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64")
val ciStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val ciKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val ciKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

val buildVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
    ?: (System.currentTimeMillis() / 1000L).toInt()

val buildVersionName = System.getenv("VERSION_NAME") ?: "debug-$buildVersionCode"

val stableKeystoreFile: File? =
    if (!ciKeystoreBase64.isNullOrBlank()) {
        File(System.getProperty("java.io.tmpdir"), "guardlink-stable-debug.jks").apply {
            writeBytes(Base64.getDecoder().decode(ciKeystoreBase64))
        }
    } else {
        null
    }

val hasStableSigning =
    stableKeystoreFile != null &&
        !ciStorePassword.isNullOrBlank() &&
        !ciKeyAlias.isNullOrBlank() &&
        !ciKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.screenlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.screenlock"
        minSdk = 26
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stableDebug") {
                storeFile = stableKeystoreFile!!
                storePassword = ciStorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
}
