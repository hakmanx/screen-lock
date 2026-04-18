plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.screenlock"
    compileSdk = 36

    signingConfigs {
        create("ciStable") {
            val storeFilePath = System.getenv("ANDROID_KEYSTORE_PATH")
            val storePasswordValue = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            val keyAliasValue = System.getenv("ANDROID_KEY_ALIAS")
            val keyPasswordValue = System.getenv("ANDROID_KEY_PASSWORD")

            if (
                !storeFilePath.isNullOrBlank() &&
                !storePasswordValue.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.screenlock"
        minSdk = 26
        targetSdk = 36

        val versionCodeFromEnv = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 2
        versionCode = versionCodeFromEnv
        versionName = "1.0.$versionCodeFromEnv"
    }

    buildTypes {
        debug {
            val stableSigningPresent = !System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()
            if (stableSigningPresent) {
                signingConfig = signingConfigs.getByName("ciStable")
            }
        }

        release {
            isMinifyEnabled = false
            val stableSigningPresent = !System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()
            if (stableSigningPresent) {
                signingConfig = signingConfigs.getByName("ciStable")
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
