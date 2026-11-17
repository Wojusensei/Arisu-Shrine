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
        versionCode = 62
        versionName = "1.1.2.0"
        // 出厂服务器地址：默认生产域名。本机联调用 -ParisuBaseUrl=http://10.0.2.2:8080
        // 覆盖，省得为跑一次模拟器就把源码里的生产地址改来改去（改完忘了还原就是事故）。
        // 写法上刻意不让模板串里出现引号：开源导出器的注释剥离器会把嵌套引号后的 // 当注释切掉
        val prodBaseUrl = "https://arisushrine.com"
        val baseUrlOverride = (project.findProperty("arisuBaseUrl") as String?)?.trimEnd('/')
        buildConfigField("String", "BASE_URL", "\"${baseUrlOverride ?: prodBaseUrl}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 正式签名：app/signing/keystore.properties（gitignored，本机保管）。
            // 文件缺失时不挂签名——别让换机器的人构建失败，也别把密钥进库
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
            // 调试签名钥匙必须恒定：跨钥匙的包无法覆盖安装（T94 教训）。
            // env.sh 把 HOME/ANDROID_USER_HOME 重定向进 .tooling，默认解析会
            // 漂到临时钥匙；app/signing/debug-keystore.properties（gitignored，
            // 本机保管）钉住真正发过版的 ~/.android/debug.keystore，
            // 文件缺失时保持默认解析
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
                // Robolectric 的 android-all jar 缓存到工作目录内，避免写到 $HOME
                it.systemProperty(
                    "robolectric.dependency.dir",
                    rootProject.file(".tooling/robolectric-deps").absolutePath,
                )
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // 用自己的属性名，避免被 roborazzi 插件的默认输出目录覆盖
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

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    // 动图（表情包的 gif）要专门的解码器，coil 核心默认只认静帧位图
    implementation(libs.coil.gif)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.zxing.core)

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
