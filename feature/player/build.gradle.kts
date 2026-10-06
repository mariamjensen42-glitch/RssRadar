// Feature 模块：音频播放页（播放控制 + 队列 + 进度）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// core:playback 提供播放能力（Media3），本模块只做界面与状态编排。
// 自带 res：player_* 专属文案留在本模块；通用 back 由 core:ui 提供，代码里别名导入 core.ui.R。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.player"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // core:ui 以 api 暴露 compose / material3 / foundation / coil / lucide，此处继承。
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:playback"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
