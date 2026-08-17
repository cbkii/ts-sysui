import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3" apply false
}

val ts18VersionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val ts18VersionName = ts18VersionProperties.getProperty("versionName")?.trim()
    ?: error("version.properties is missing versionName")
val ts18VersionCode = ts18VersionProperties.getProperty("versionCode")?.trim()?.toIntOrNull()
    ?: error("version.properties has an invalid versionCode")
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(ts18VersionName)) {
    "versionName must use x.y.z format"
}
require(ts18VersionCode > 0) { "versionCode must be positive" }

extra["ts18VersionName"] = ts18VersionName
extra["ts18VersionCode"] = ts18VersionCode
