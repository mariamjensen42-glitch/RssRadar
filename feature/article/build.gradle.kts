// Feature 模块：阅读页（正文渲染 + 划线标注 + AI 面板 + 翻译 + 图集）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 迁移要点：3 条前置在 fb35417 里已解开（LocalReadingPrefs/ApplySystemBarIcons 沉 core.ui.theme、
// uiRes 沉 core.ui.labels、FetchFailure/ExtractionIssue 上移 core:model）。
// 自带 res：article 域文案（ai_* 的阅读侧、reader_*、reading_* 等）。
// 通用词（back/cancel/save/close）由 core:ui 提供，代码里别名导入 core.ui.R。
// 守卫测试故意留在 app/src/test —— CI 只跑 :app:testDebugUnitTest。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.article"
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

    // 正文渲染（ReadingNodes / ReadingDenoise）直接在 UI 侧解析 HTML。
    implementation(libs.jsoup)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
