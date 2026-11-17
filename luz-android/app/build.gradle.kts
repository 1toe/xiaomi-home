plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

base { archivesName = "luz" }
android {
    namespace = "app.toetoetoe.luz"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.toetoetoe.luz"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }
    signingConfigs {
        create("stable") {
            storeFile = file(System.getenv("LUZ_KEYSTORE") ?: "luz.keystore")
            storePassword = System.getenv("LUZ_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("LUZ_KEY_ALIAS")
            keyPassword = System.getenv("LUZ_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("stable")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
