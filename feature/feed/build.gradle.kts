// Feature 模块：信息流（列表 + 粘性日期头 + 卡片排版 + 单源文章页）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 迁移要点见 .workbuddy/memory/2026-10-04.md：三条 app 包越界边（LocalListDisplay / i18n.labelRes /
// R.string）已分别下沉 core:ui、随域搬入本模块、搬进本模块 res。
// 自带 res：feed 域 89 条文案（day_*/month_*/weekday_*/vm_*/feed_*/mark_* 等）。
// 通用 back 由 core:ui 提供，代码里别名导入 core.ui.R。
// 守卫测试（ScrollSlotsTest / DayGroupsTest / ContentTypePartitionTest）**故意留在 app/src/test**：
// CI 只跑 :app:testDebugUnitTest，本模块测试不会被 CI 执行。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.feed"
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

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
