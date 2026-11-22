plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.herfar.barodial"
    // AndroidX(core 1.19, Compose 1.12)가 compileSdk 37 이상을 요구한다.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.herfar.barodial"
        minSdk = 29
        // Galaxy Z Fold7 출시 OS(Android 16, API 36) 기준
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 저장소에 포함된 공용 디버그 키. 어느 PC에서 빌드해도 같은 서명이 되어
        // 기존 설치본 위에 덮어쓰기(업데이트) 설치가 가능하다. 개인 사용 전용.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 개인용 직접 설치 APK 이므로 디버그 키로 서명한다 (스토어 배포 금지).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // 통화기록 기능은 권한 정책 때문에 별도 빌드 변형으로 분리한다.
    //  - standard : 기본(프로덕션) 빌드. READ_CALL_LOG 를 선언하지 않는다.
    //  - calllog  : 실험용 빌드. READ_CALL_LOG 를 선언하고 실제 기기에서 허용 여부를 검증한다.
    flavorDimensions += "callhistory"
    productFlavors {
        create("standard") {
            dimension = "callhistory"
            isDefault = true
        }
        create("calllog") {
            dimension = "callhistory"
            versionNameSuffix = "-calllog"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("barodial.screenshotDir", rootProject.file("docs/screenshots").absolutePath)
                it.maxHeapSize = "3g"
                // JDK 17+ 에서 Robolectric 이 Android 런타임 내부를 다룰 수 있도록 모듈 접근을 연다.
                it.jvmArgs(
                    "--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens", "java.base/java.io=ALL-UNNAMED",
                    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                )
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
