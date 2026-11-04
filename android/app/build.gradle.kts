plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.equipx.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.equipx.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Roborazzi's Gradle plugin does not support AGP 9 yet, so only the library is used. Screenshot tests live in
// com.equipx.app.screenshots and write PNGs to src/test/snapshots/ only when this task is requested.
val recordRequested = gradle.startParameter.taskNames.any { it.endsWith("recordRoborazziDebug") }
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", recordRequested.toString())
    systemProperty("roborazzi.output.dir", layout.projectDirectory.dir("src/test/snapshots").asFile.absolutePath)
    // Screenshot tests are slow; plain testDebugUnitTest skips them unless recording.
    if (!recordRequested) filter.excludeTestsMatching("com.equipx.app.screenshots.*")
}
tasks.register("recordRoborazziDebug") {
    group = "verification"
    description = "Renders key screens with fake data to app/src/test/snapshots/ (Robolectric + Roborazzi)."
    dependsOn("testDebugUnitTest")
}
