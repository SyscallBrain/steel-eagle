plugins {
    id("com.android.application")
}

android {
    namespace = "pt.tiago.steeleagle"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "pt.tiago.steeleagle"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "1.0.0"
    }

    // Signing keys live outside the repository (~/.gradle/gradle.properties).
    // Without these properties (CI, forks) the release build is produced unsigned.
    val releaseStoreFile = providers.gradleProperty("steelEagle.release.storeFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = providers.gradleProperty("steelEagle.release.storePassword").get()
            keyAlias = providers.gradleProperty("steelEagle.release.keyAlias").get()
            keyPassword = providers.gradleProperty("steelEagle.release.keyPassword").get()
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
