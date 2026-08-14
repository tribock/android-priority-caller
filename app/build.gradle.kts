import java.util.Properties

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
        // Overridable from CI so releases can carry a real semantic version, e.g.:
        //   ./gradlew assembleRelease -PversionNameOverride=1.4.0 -PversionCodeOverride=42
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionNameOverride") as String?) ?: "1.0"
    }

    // Release signing comes from your own keystore, never committed. Locally, put
    // JKS_STORE_PATH/JKS_STORE_PW/JKS_KEY_ALIAS/JKS_KEY_PW in local.properties
    // (already gitignored). In CI, the same names are set as environment variables
    // from GitHub Actions secrets — see .github/workflows/release.yml.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingProp(name: String): String? = System.getenv(name) ?: localProps.getProperty(name)

    signingConfigs {
        create("release") {
            signingProp("JKS_STORE_PATH")?.let { storeFile = file(it) }
            storePassword = signingProp("JKS_STORE_PW")
            keyAlias = signingProp("JKS_KEY_ALIAS")
            keyPassword = signingProp("JKS_KEY_PW")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
