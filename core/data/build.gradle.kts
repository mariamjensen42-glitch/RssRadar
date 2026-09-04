// Android library 模块：承载数据层（Repository、Room DB、解析、存储偏好、通知、AI）。
// 依赖 core:model（领域数据结构）与 core:domain（纯领域逻辑/HTTP 抓取），
// 并把 android.util.Log 之外的框架依赖（Room、ROME、jsoup、NotificationCompat）收拢在本模块。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cycling.rssradar.core.data"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // rome 2.1.0 的 OSGi 内嵌 jar 与 app 同样的 "2 files found with path" 问题，
    // 在本模块打包时排除（app 模块的 excludes 不传递到依赖模块）。
    packaging {
        resources {
            excludes += setOf("rome-utils-2.1.0.jar", "rome-2.1.0.jar")
        }
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // javax.inject（@Inject/@Singleton）来自 hilt-android 传递依赖
    implementation(libs.hilt.android)

    implementation(libs.rome)
    implementation(libs.rome.modules)
    implementation(libs.jsoup)
    implementation(libs.readability4j)

    testImplementation(libs.junit)
}
