import java.util.Properties
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystoreProperties = Properties()
val fileLocalKeystoreProps = rootProject.file("keystore.properties")
val fileSharedKeystoreProps = File(rootProject.projectDir.parentFile.parentFile, "Personal/Android KeyStore/keystore.properties")
val fileResolvedKeystoreProps = when {
    fileLocalKeystoreProps.exists() -> fileLocalKeystoreProps
    fileSharedKeystoreProps.exists() -> fileSharedKeystoreProps
    else -> null
}
if (fileResolvedKeystoreProps != null && fileResolvedKeystoreProps.exists()) {
    fileResolvedKeystoreProps.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.antigravity.triggeredwallpaper"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.antigravity.triggeredwallpaper"
        minSdk = 24
        targetSdk = 36
        versionCode = 112
        versionName = "1.1.2"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                val stringStoreFile = keystoreProperties.getProperty("storeFile")
                val fileStore = when {
                    rootProject.file(stringStoreFile).exists() -> rootProject.file(stringStoreFile)
                    File(fileResolvedKeystoreProps?.parentFile, stringStoreFile).exists() -> File(fileResolvedKeystoreProps?.parentFile, stringStoreFile)
                    else -> file(stringStoreFile)
                }
                storeFile = fileStore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                val debugSigning = signingConfigs.getByName("debug")
                storeFile = debugSigning.storeFile
                storePassword = debugSigning.storePassword
                keyAlias = debugSigning.keyAlias
                keyPassword = debugSigning.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // DocumentFile & SAF
  implementation(libs.androidx.documentfile)

  // Window Manager for Foldable posture
  implementation(libs.androidx.window)

  // Location & Geofencing
  implementation(libs.play.services.location)

  // WorkManager
  implementation(libs.androidx.work.runtime.ktx)

  // Serialization
  implementation(libs.kotlinx.serialization.json)

  // Extended Icons
  implementation(libs.androidx.compose.material.icons.extended)

  // Coil
  implementation(libs.coil.compose)

  // AndroidX WebKit
  implementation("androidx.webkit:webkit:1.12.1")
}
