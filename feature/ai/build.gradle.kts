// Feature 模块：AI 能力管理（功能开关 / 产物列表 / 用量预算 / 兴趣画像 / 提示词模板 / 任务队列）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 与 feature:me 的分界：本模块是 AI 家族（Ai* + PromptTemplates + InterestProfile），
// me 那边是统计/备份/崩溃日志/抓取诊断/Tips。两组实测零交叉引用。
// 自带 res：ai_* 与 artifacts_* / budget_* / profile_* / prompt_* 文案。
// 守卫测试故意留在 app/src/test —— CI 只跑 :app:testDebugUnitTest。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.ai"
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
