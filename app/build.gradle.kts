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
        versionCode = 47
        versionName = "3.14"
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
        // The development APK is a SECOND installed Android app, NOT an update
        // of the user's Torima v3.14. Do not change release applicationId.
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appDisplayName"] = "Torima Auto DEV"
        }
        // Reproducibly signed for repeatable phone tests. Different appId
        // and independent data sandbox from both production and debug.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            val qaCode = System.getenv("QA_VERSION_CODE")?.toIntOrNull()
            if (qaCode != null) {
                require(qaCode in 100001..2100000000)
            }
            versionNameSuffix = "-qa"
            manifestPlaceholders["appDisplayName"] = "Torima Auto QA"
            signingConfig = signingConfigs.getByName("distribution")
            matchingFallbacks += listOf("release")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("distribution")
            manifestPlaceholders["appDisplayName"] = "Torima Auto"
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
    implementation(project(":qa-updater"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}

// Public Android Components API: QA output only, stable versionCode stays 47.
androidComponents {
    onVariants(selector().withBuildType("qa")) { variant ->
        System.getenv("QA_VERSION_CODE")?.toInt()?.let { code ->
            require(code in 100001..2100000000)
            variant.outputs.forEach { output ->
                output.versionCode.set(code)
                output.versionName.set("${android.defaultConfig.versionName}-qa.$code")
            }
        }
    }
}
