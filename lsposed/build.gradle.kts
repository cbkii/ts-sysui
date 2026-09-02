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

    namespace = "au.com.cb.ts18.statusbar.input"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "au.com.cb.ts18.statusbar.input"
        minSdk = 29
        targetSdk = 29
        versionCode = ts18VersionCode
        versionName = ts18VersionName
        buildConfigField("boolean", "TS18_DIAGNOSTIC", "false")
        buildConfigField("String", "TS18_BUILD_KIND", "\"base\"")
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
        debug {
            buildConfigField("boolean", "TS18_DIAGNOSTIC", "true")
            buildConfigField("String", "TS18_BUILD_KIND", "\"debug\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "TS18_DIAGNOSTIC", "false")
            buildConfigField("String", "TS18_BUILD_KIND", "\"release\"")
            if (releaseSigning != null) signingConfig = releaseSigning
        }
        create("diagnostic") {
            initWith(getByName("release"))
            isDebuggable = true
            versionNameSuffix = "-diagnostic"
            buildConfigField("boolean", "TS18_DIAGNOSTIC", "true")
            buildConfigField("String", "TS18_BUILD_KIND", "\"diagnostic\"")
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release", "debug")
        }
    }
}

dependencies {
    compileOnly(project(":xposed-stubs"))
    testImplementation("junit:junit:4.13.2")
}
