import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val wgerServerUrl = localProperties.getProperty("wger.server.url", "http://100.64.0.1:8000")
    .removeSurrounding("\"")
val wgerApiToken = localProperties.getProperty("wger.api.token", "")
    .removeSurrounding("\"")

android {
    namespace = "com.wger.companion.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wger.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "WGER_SERVER_URL", "\"$wgerServerUrl\"")
        buildConfigField("String", "WGER_API_TOKEN", "\"$wgerApiToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // android.util.Log y demás devuelven valores por defecto en tests JVM
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)
    implementation(libs.coroutines.android)

    // Cliente HTTP Ktor y serialización
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // AndroidX & Compose UI Móvil
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
}
