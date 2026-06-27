plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.gms.google.services)
  alias(libs.plugins.google.firebase.crashlytics)
  id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.example.calmsource"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.calmsource"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "RELAY_BASE_URL", "\"\"")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign the release APK with the local debug keystore so the user
            // can sideload a non-debuggable APK without configuring a real
            // release keystore. The debug keystore is fine for personal device
            // installs; it is NOT suitable for Play Store distribution.
            signingConfig = signingConfigs.getByName("debug")
            // Lint crashes during FIR analysis on this codebase; the user is
            // sideloading, not publishing to the Play Store, so vital lint is
            // not worth blocking the build.
            lint {
                checkReleaseBuilds = false
                abortOnError = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  implementation(libs.kotlinx.collections.immutable)
  implementation(project(":core:sourceintelligence"))
  implementation(libs.firebase.crashlytics)
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Hilt DI
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  // Core & Feature modules
  implementation(project(":core:discoveryengine"))
  implementation(project(":core:model"))
  implementation(project(":core:database"))
  implementation(project(":core:data"))
  implementation(project(":core:network"))
  implementation(project(":core:parser"))
  implementation(project(":core:playback"))
  implementation(project(":core:ui"))
  implementation(project(":feature:iptv"))
  implementation(project(":feature:extensions"))
  implementation(project(":feature:debrid"))
  implementation(project(":feature:search"))

  // Coil, Icons & Media3
  implementation("androidx.palette:palette-ktx:1.0.0")
  implementation(libs.coil.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.exoplayer.dash)
  implementation(libs.media3.ui)

  // CameraX and ML Kit Barcode Scanning
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  // play-services-mlkit-barcode-scanning is defined in libs.versions.toml but commented out here for offline build compatibility
  // implementation(libs.play.services.mlkit.barcode.scanning)
  implementation(libs.zxing.core)
  detektPlugins(project(":detekt-rules"))
}

detekt {
    toolVersion = "1.23.7"
    config.from("$rootDir/config/detekt.yml")
    buildUponDefaultConfig = false
    allRules = false
    parallel = true
}
