import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google Drive and similar sync clients can lock app/build while AGP replaces APK
// output directories on Windows. Keep generated files outside synced repositories.
if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
    val windowsBuildDir = providers.environmentVariable("GYMLOG_BUILD_DIR").orNull
        ?: System.getenv("LOCALAPPDATA")?.let { "$it/GymLog/build/app" }
    if (!windowsBuildDir.isNullOrBlank()) {
        layout.buildDirectory.set(file(windowsBuildDir))
    }
}

val releaseSigningConfigured = listOf(
    "GYMLOG_KEYSTORE_FILE",
    "GYMLOG_KEYSTORE_PASSWORD",
    "GYMLOG_KEY_ALIAS",
    "GYMLOG_KEY_PASSWORD",
).all { !System.getenv(it).isNullOrBlank() }
val buildTimestamp = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))

android {
    namespace = "com.gymlog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gymlog.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
        buildConfigField("String", "BUILD_TIME", 34.toChar() + buildTimestamp + 34.toChar())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(System.getenv("GYMLOG_KEYSTORE_FILE"))
                storePassword = System.getenv("GYMLOG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GYMLOG_KEY_ALIAS")
                keyPassword = System.getenv("GYMLOG_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        htmlReport = true
        textReport = true
        warningsAsErrors = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.health.connect:connect-client:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
