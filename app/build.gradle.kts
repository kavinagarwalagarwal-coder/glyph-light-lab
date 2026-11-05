plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.glyphlab.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glyphlab.app"
        // Nothing's Glyph toolkit needs Android 13+ (it only works on Android 14+).
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Nothing's official Glyph Developer Kit:
    // https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
}
