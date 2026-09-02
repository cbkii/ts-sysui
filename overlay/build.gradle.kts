plugins {
    id("com.android.application")
}

val ts18VersionName = rootProject.extra["ts18VersionName"] as String
val ts18VersionCode = rootProject.extra["ts18VersionCode"] as Int

android {
    val releaseKeystorePath = System.getenv("TS18_KEYSTORE_PATH")
    val releaseSigning = if (!releaseKeystorePath.isNullOrBlank()) {
        val storePasswordValue = System.getenv("TS18_KEYSTORE_PASSWORD")
        val keyAliasValue = System.getenv("TS18_KEY_ALIAS")
        val keyPasswordValue = System.getenv("TS18_KEY_PASSWORD")
        require(!storePasswordValue.isNullOrBlank()) { "TS18_KEYSTORE_PASSWORD is required when TS18_KEYSTORE_PATH is set" }
        require(!keyAliasValue.isNullOrBlank()) { "TS18_KEY_ALIAS is required when TS18_KEYSTORE_PATH is set" }
        require(!keyPasswordValue.isNullOrBlank()) { "TS18_KEY_PASSWORD is required when TS18_KEYSTORE_PATH is set" }
        signingConfigs.create("ts18Release") {
            storeFile = file(releaseKeystorePath)
            storePassword = storePasswordValue
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
        }
    } else null

    namespace = "au.com.cb.ts18.statusbar.geometry.overlay"
    compileSdk = 35

    defaultConfig {
        applicationId = "au.com.cb.ts18.statusbar.geometry.overlay"
        minSdk = 29
        targetSdk = 29
        versionCode = ts18VersionCode
        versionName = ts18VersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigning != null) signingConfig = releaseSigning
        }
        create("diagnostic") {
            initWith(getByName("release"))
            versionNameSuffix = "-diagnostic"
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release", "debug")
        }
    }
}
