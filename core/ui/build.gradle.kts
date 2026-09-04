// Android library 模块：UI 基石（主题、通用组件、图片封装）。
// 铁律：不依赖 core:data / core:domain / di / sync——主题与组件全部参数化，
// 数据层状态由 app 的 CompositionLocalRoot 装配后经参数/CompositionLocal 进入。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cycling.rssradar.core.ui"
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
    api(project(":core:model"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.runtime.saveable)

    api(libs.coil.compose)
    api(libs.coil.network.okhttp)
    api(libs.compose.icons.lucide)

    testImplementation(libs.junit)
}
