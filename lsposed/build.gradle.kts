plugins {
    id("com.android.application")
}

android {
    val releaseKeystorePath = System.getenv("TS18_KEYSTORE_PATH")
    val releaseSigning = if (!releaseKeystorePath.isNullOrBlank()) {
        signingConfigs.create("ts18Release") {
            storeFile = file(releaseKeystorePath)
            storePassword = System.getenv("TS18_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("TS18_KEY_ALIAS")
            keyPassword = System.getenv("TS18_KEY_PASSWORD")
        }
    } else null

    namespace = "au.com.cb.ts18.statusbar.input"
    compileSdk = 35

    defaultConfig {
        applicationId = "au.com.cb.ts18.statusbar.input"
        minSdk = 29
        targetSdk = 29
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigning != null) signingConfig = releaseSigning
        }
    }
}
dependencies {
    compileOnly(project(":xposed-stubs"))
    testImplementation("junit:junit:4.13.2")
}
