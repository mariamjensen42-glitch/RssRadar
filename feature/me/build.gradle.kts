// Feature 模块：我的（阅读统计 / 备份恢复 / 崩溃日志 / 抓取诊断 / Tips）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 与 feature:ai 的分界：这里是不带 AI 语义的运维与统计页；两组实测零交叉引用。
// 自带 res：stats_* / backup_* / crash_* / diag_* / tips_* 文案；
// 其中 8 条入口标题（stats_title / backup_title / crash_title 等）在 feature:settings 也在用 ⇒ 双留副本。
// 守卫测试故意留在 app/src/test —— CI 只跑 :app:testDebugUnitTest。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.me"
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
