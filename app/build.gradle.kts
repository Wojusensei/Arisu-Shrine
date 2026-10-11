import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.arisu.shrine"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.arisu.shrine"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "1.0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            val ksPropsFile = rootProject.file("app/signing/keystore.properties")
            if (ksPropsFile.exists()) {
                val ksProps = Properties().apply { ksPropsFile.inputStream().use { load(it) } }
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(ksProps.getProperty("storeFile"))
                    storePassword = ksProps.getProperty("storePassword")
                    keyAlias = ksProps.getProperty("keyAlias")
                    keyPassword = ksProps.getProperty("keyPassword")
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"

            val dbgPropsFile = rootProject.file("app/signing/debug-keystore.properties")
            if (dbgPropsFile.exists()) {
                val dbgProps = Properties().apply { dbgPropsFile.inputStream().use { load(it) } }
                signingConfig = signingConfigs.getByName("debug").apply {
                    storeFile = file(dbgProps.getProperty("storeFile"))
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {

                it.systemProperty(
                    "robolectric.dependency.dir",
                    rootProject.file(".tooling/robolectric-deps").absolutePath,
                )
                it.systemProperty("robolectric.graphicsMode", "NATIVE")

                it.systemProperty(
                    "arisu.screenshot.dir",
                    rootProject.file("screenshots").absolutePath,
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    debugImplementation(libs.androidx.ui.test.manifest)
}
