// Feature 模块：订阅管理（订阅源列表/分组/批量整理 + 单源操作页）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 不含 res：本模块还没有任何 R.string —— 文案目前是硬编码中文（141 条），
// 属双语路线的欠账，待资源化批次统一处理，与模块边界无关。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.subscriptions"
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
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    // ActivityResultContracts（导入订阅文件用）
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
