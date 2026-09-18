pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "TarsosDSP"
            url = uri("https://mvn.0110.be/releases")
        }
    }
}

rootProject.name = "KidsPiano"
include(":core:common")
include(":core:notes")
include(":core:pitch")
include(":core:calibration")
include(":core:learning")

val localProps = java.util.Properties()
val localFile = file("local.properties")
if (localFile.exists()) localFile.inputStream().use { localProps.load(it) }
val sdkDir = sequenceOf(
    localProps.getProperty("sdk.dir"),
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
).firstOrNull { !it.isNullOrBlank() }

if (sdkDir != null && java.io.File(sdkDir).isDirectory) {
    include(":core:audio")
    include(":app")
} else {
    logger.warn("Android SDK not found; configuring JVM cores only. Open android/ in Android Studio to build Audio Lab.")
}
