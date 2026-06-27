plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.7")
}
