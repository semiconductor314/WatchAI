plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.semi.watchai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.semi.watchai"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 原生库只打包会用到的 ABI：
        // - arm64-v8a：Wear OS 3+ 真机
        // - armeabi-v7a：32 位手表（当前真机调试目标）
        // - x86_64：模拟器
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            // 调试版包名加 .debug 后缀：与已安装的正式版（签名不同）共存，可同时安装。
            // 注意：后缀只影响 applicationId，namespace/代码包名不变；但调试版有独立的
            // 应用数据目录 —— 正式版的聊天记录不会出现在调试版里。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ratex.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}