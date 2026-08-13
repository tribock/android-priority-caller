plugins {
    // AGP 9+ compiles Kotlin sources itself — no separate
    // org.jetbrains.kotlin.android plugin needed or wanted here.
    id("com.android.application")
}

android {
    namespace = "com.prioritycaller.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prioritycaller.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
    }
}

// Built-in Kotlin (AGP 9+) configures the compiler through the `kotlin {}` block
// instead of the old android.kotlinOptions {}.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
