// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.hilt.android) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.google.gms.google.services) apply false
  alias(libs.plugins.google.firebase.crashlytics) apply false
  id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}

subprojects {
    if (name != "detekt-rules") {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        dependencies {
            add("detektPlugins", project(":detekt-rules"))
        }

        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            toolVersion = "1.23.7"
            config.from(files("${rootProject.projectDir}/config/detekt.yml"))
            buildUponDefaultConfig = false
            allRules = false
            parallel = true
        }
    }
}