import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

// local.properties is git-ignored. Developer Mode registered on the real glasses with both values
// blank (hub/status/kchperla.md, 2026-10-24); META_SUITE 3.2 says "0".
val localProperties =
    Properties().apply {
      val file = rootDir.resolve("local.properties")
      if (file.exists()) file.inputStream().use { load(it) }
    }

// Muse key from the repo's git-ignored .env (team name MUSE_API_KEY; MODEL_API_KEY also read).
// It is compiled into debug APKs, so never share an APK built with a key.
val dotEnv: Map<String, String> =
    rootDir.resolve("../.env").takeIf { it.exists() }?.readLines().orEmpty()
        .mapNotNull { line ->
          line.trim().takeIf { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
              ?.let { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
        }
        .toMap()
val museApiKey = dotEnv["MUSE_API_KEY"] ?: dotEnv["MODEL_API_KEY"] ?: ""

// D12: automatic face identification is off unless a human sets myst.allowFaceId=true here.
val allowFaceId = localProperties.getProperty("myst.allowFaceId", "false") == "true"

android {
  namespace = "com.myst"
  compileSdk = 36

  buildFeatures { buildConfig = true }

  defaultConfig {
    applicationId = "com.myst"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "0.1"

    manifestPlaceholders["mwdat_application_id"] =
        localProperties.getProperty("mwdat_application_id", "")
    manifestPlaceholders["mwdat_client_token"] =
        localProperties.getProperty("mwdat_client_token", "")

    buildConfigField("String", "MUSE_API_KEY", "\"${museApiKey.replace("\"", "")}\"")
    buildConfigField("boolean", "ALLOW_FACE_ID", allowFaceId.toString())
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.material3)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.exifinterface)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.camera)
  implementation(libs.mwdat.speech)
  implementation(libs.mwdat.mockdevice)
}
