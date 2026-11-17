import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Keep generated output ignored and on the project drive: Android resource-backed
// unit tests need relative paths between this module and the build directory.
if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
    val windowsBuildDir = providers.environmentVariable("GYMLOG_BUILD_DIR").orNull
        ?: rootProject.file("../.tools/b").absolutePath
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
// Keep debug updates compatible across Windows accounts without sharing a private key in Git.
val localBuildProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val buildTimestamp = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))

android {
    namespace = "com.gymlog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gymlog.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.0"
        buildConfigField("String", "BUILD_TIME", 34.toChar() + buildTimestamp + 34.toChar())
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

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
        getByName("debug") {
            localBuildProperties.getProperty("gymlog.debug.keystore")?.takeIf { it.isNotBlank() }?.let {
                storeFile = rootProject.file(it)
            }
        }
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
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.health.connect:connect-testing:1.0.0-alpha03")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
