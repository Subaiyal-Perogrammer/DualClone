plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}

// Read by the vendored BlackBox engine module (Bcore/build.gradle, Groovy):
//   compileSdkVersion -> its compileSdk
//   minSdk            -> its minSdkVersion (engine supports API 21+; the app itself requires 26)
extra["compileSdkVersion"] = 34
extra["minSdk"] = 21
