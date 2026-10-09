plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.neru.powlautotest"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.neru.powlautotest"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "1.0"
    }
    signingConfigs {
        create("distribution") {
            val store = System.getenv("TORIMA_KEYSTORE")
            if (!store.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = System.getenv("TORIMA_STORE_PASSWORD")
                keyAlias = System.getenv("TORIMA_KEY_ALIAS")
                keyPassword = System.getenv("TORIMA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
