// Android library 模块：后台播放（Media3 ExoPlayer + MediaSessionService）。
// 依赖方向：只依赖 AndroidX / Hilt / coroutines，不依赖 core:data / core:domain ——
// 播放能力与数据层解耦，feature:player 可直接依赖本模块。
// 注意：PlaybackService 由 app 的 AndroidManifest 声明（android:name 用全限定名）。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.core.playback"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
