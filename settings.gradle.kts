pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack: required by the vendored BlackBox engine
        // (com.github.tiann:FreeReflection). This is a real, resolvable
        // repository — the engine has no Maven Central artifact.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "DualClone"
include(":app")
// Vendored BlackBox virtualization engine (see blackbox/UPSTREAM.md)
include(":Bcore")
include(":black-reflection")
include(":compiler")
