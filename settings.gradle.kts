pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Parropeato"

val pathSmartFoo = run {
    val localPropertiesFile = rootProject.projectDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        val properties = java.util.Properties().apply {
            localPropertiesFile.inputStream().use { load(it) }
        }
        properties.getProperty("PATH_SMARTFOO")
    } else {
        null
    }
}
if (!pathSmartFoo.isNullOrEmpty()) {
    val smartFooFile = file(pathSmartFoo)
    if (smartFooFile.exists()) {
        includeBuild(smartFooFile.parentFile) {
            dependencySubstitution {
                substitute(module("com.smartfoo:smartfoo-android-lib-core")).using(project(":smartfoo-android-lib-core"))
            }
        }
    }
}

include(":common")
include(":wear")
include(":mobile")
