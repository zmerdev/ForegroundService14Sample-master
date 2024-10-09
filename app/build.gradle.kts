plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.landomen.sample.foregroundservice14"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.landomen.sample.foregroundservice14"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.google.play.services.location)
    implementation(libs.androidx.appcompat)
    implementation ("de.proglove:connect-sdk:1.9.2")
    // Ktor WebSockets support
    implementation ("io.ktor:ktor-server-core:2.3.4")
    implementation ("io.ktor:ktor-server-websockets:2.3.4")
    implementation ("io.ktor:ktor-server-netty:2.3.4") // Netty engine for Ktor

    // Content negotiation (for JSON serialization, if needed)
    implementation ("io.ktor:ktor-server-content-negotiation:2.3.4")
    implementation ("io.ktor:ktor-serialization-kotlinx-json:2.3.4")

    // Other dependencies (ensure you have the following)
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4") // For coroutines
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4") // For coroutines on Android

    // (Optional) Logging and debugging for Ktor
    implementation ("io.ktor:ktor-server-call-logging:2.3.4")
}
