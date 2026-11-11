plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.duofold.blur"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.duofold.blur"
        // AGSL / RuntimeShader 自 API 33 (Android 13) 起才可用，这是硬门槛。
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 刻意保持零第三方依赖：只用 Android 框架 API，避免依赖解析失败导致编译不了。
dependencies { }
