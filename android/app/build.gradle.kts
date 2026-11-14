plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.nexora.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nexora.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 100
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"${project.findProperty("NEXORA_API_BASE_URL") ?: "https://apex-oknl.onrender.com/"}\"")
    }
    signingConfigs {
        create("stableDebug") {
            storeFile = file("nexora-debug.keystore")
            enableV1Signing = true
            enableV2Signing = true
            storePassword = "nexoraDebug2026"
            keyAlias = "nexora"
            keyPassword = "nexoraDebug2026"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("stableDebug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
