plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.calmsource.core.network"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = 23
        buildConfigField("String", "WS_AUTH_URL", "\"ws://167.233.92.78:3000/tv-auth\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"http://167.233.92.78\"")
        buildConfigField("String", "RELAY_API_URL", "\"http://167.233.92.78/api/relay\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.client.logging)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.client.websockets)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.core)
}
