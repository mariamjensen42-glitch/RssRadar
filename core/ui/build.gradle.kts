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
    // core:model：labels/ 下的共享枚举文案扩展（如 LibraryRange.labelRes）要引用枚举类型本身。
    // 仅此一处用途，方向合法——铁律禁的是 core:data / core:domain。
    implementation(project(":core:model"))

    // WindowCompat（ApplySystemBarIcons 补系统栏图标色）来自 androidx.core。
    implementation(libs.androidx.core.ktx)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.runtime.saveable)

    // MVI 契约（core.ui.mvi）把 StateFlow/SharedFlow 作为公开 API 类型暴露，
    // 必须走 api，否则实现方拿不到 coroutines 类型。
    api(libs.kotlinx.coroutines.core)

    api(libs.coil.compose)
    api(libs.coil.network.okhttp)
    api(libs.compose.icons.lucide)

    testImplementation(libs.junit)
}
