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
    namespace = "com.example.calmsource.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.calmsource.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "RELAY_BASE_URL", "\"\"")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
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
      jniLibs {
        // Older Fire OS installers fail with res=-113 when native libraries are
        // mmap-loaded directly from the APK. Package them for legacy extraction.
        useLegacyPackaging = true
      }
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

  // Compose TV Libraries
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.tvprovider)
  implementation(libs.androidx.tv.material)

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
  implementation(libs.coil.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.exoplayer.dash)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.zxing.core)
  implementation("androidx.palette:palette-ktx:1.0.0")
  detektPlugins(project(":detekt-rules"))
}

detekt {
    toolVersion = "1.23.7"
    config.from("$rootDir/config/detekt.yml")
    buildUponDefaultConfig = false
    allRules = false
    parallel = true
}
