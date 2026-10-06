// Feature 模块：添加订阅（RSSHub 目录浏览 + 参数表单 + 预览 + 提交）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// 不含 res：本 feature 全量走 R.string 之外的路径，文案由 core:ui / 调用方提供。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.addsubscription"
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

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    // Destination 里用 hiltViewModel()：该符号由传递依赖 hilt-lifecycle-viewmodel-compose 提供，
    // 只声明 hilt-android + hilt-compiler 拿不到——本地扁平 classpath 恰好有它，真 Gradle 没有，
    // 症状就是 AS 报 Unresolved reference 'hilt'。
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
