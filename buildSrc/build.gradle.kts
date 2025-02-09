plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.shadow)
    implementation(libs.config)
    implementation(libs.asm)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.plasmoverse.com/releases")
}
