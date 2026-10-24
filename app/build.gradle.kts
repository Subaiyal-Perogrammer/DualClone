plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dualclone.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dualclone.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            // The vendored BlackBox engine ships native hooks for these ABIs.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "commonMain/**"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
        }
    }
}

configurations.configureEach {
    exclude(group = "androidx.collection", module = "collection-ktx")
}

dependencies {
    // BlackBox virtualization engine — vendored source module (Apache-2.0,
    // see blackbox/UPSTREAM.md). The ONLY component allowed to install or
    // launch other apps, inside its virtual environment.
    implementation(project(":Bcore"))
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-android:1.6.2")
    implementation("androidx.compose.ui:ui-android:1.6.2")
    implementation("androidx.compose.ui:ui-graphics-android:1.6.2")
    implementation("androidx.compose.ui:ui-text-android:1.6.2")
    implementation("androidx.compose.ui:ui-unit-android:1.6.2")
    implementation("androidx.compose.ui:ui-util-android:1.6.2")
    implementation("androidx.compose.ui:ui-geometry-android:1.6.2")
    implementation("androidx.compose.ui:ui-tooling-preview-android:1.6.2")
    implementation("androidx.compose.foundation:foundation-android:1.6.2")
    implementation("androidx.compose.foundation:foundation-layout-android:1.6.2")
    implementation("androidx.compose.animation:animation-android:1.6.2")
    implementation("androidx.compose.animation:animation-core-android:1.6.2")
    implementation("androidx.compose.runtime:runtime-android:1.6.2")
    implementation("androidx.compose.runtime:runtime-saveable-android:1.6.2")
    implementation("androidx.compose.material3:material3-android:1.2.0")
    implementation("androidx.compose.material:material-icons-core-android:1.6.2")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.2")
    implementation("androidx.compose.material:material-ripple-android:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
    // Room (clone records)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
