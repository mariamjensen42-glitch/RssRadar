// Feature 模块：设置（设置中心 + 通用/同步/RSSHub/AI 诊断，并收编原 ui/me 的通知与过滤规则）。
// 依赖方向：feature → core。禁止依赖 app，也禁止依赖其他 feature。
// i18n 前置已就位：UiText/resolve 来自 core.ui.text，通用词（back/select/enter/save/action_confirm）来自 core:ui，
// 设置域枚举文案（SettingsTexts）与过滤规则文案（FilterRuleTexts）随本模块走。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar.ui.settings"
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
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
