plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.calmsource.feature.search"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:discoveryengine"))
    implementation(project(":core:network"))
    implementation(project(":feature:extensions"))
    implementation(project(":feature:iptv"))
    implementation(project(":feature:debrid"))
    implementation(project(":core:parser"))
    implementation(project(":core:sourceintelligence"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.security.crypto)
    testImplementation(project(":core:network"))
    testImplementation(project(":feature:debrid"))
    testImplementation(project(":core:model"))
    testImplementation(project(":core:database"))
}
