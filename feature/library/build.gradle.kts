// Feature 模块：收藏与稍后读（两 tab 共用排序 / 时间范围 / 源筛选，多选批量整理）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 分页共用 core.ui.paging.PagedSnapshot；文案：library_* 专属留在本模块，
// 通用 back 由 core:ui 提供，代码里别名导入 core.ui.R。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.library"
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

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
