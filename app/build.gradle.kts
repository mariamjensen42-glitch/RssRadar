plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cycling.rssradar"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cycling.rssradar"
        minSdk = 31
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI 签名（Release workflow）：密钥经 GitHub Secrets 以环境变量注入。
    // 本地没有这些环境变量时自动缺省，日常构建不受影响。
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("RSSRADAR_KEYSTORE_PATH") ?: return@create
            storeFile = file(ksPath)
            storePassword = System.getenv("RSSRADAR_STORE_PASSWORD")
            keyAlias = System.getenv("RSSRADAR_KEY_ALIAS")
            keyPassword = System.getenv("RSSRADAR_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // AGP 9.3+ 新版 optimization DSL：enable = true 一次性启用代码优化（R8）
            // 与优化版资源裁剪，二者绑定，无需再写 isShrinkResources；平台默认 keep 规则
            // 也自动包含（等价 proguard-android-optimize.txt）。
            // keep 规则统一放 app/src/main/keepRules/*.keep（.keep 后缀源集自动收集）。
            // 回退：改回 enable = false 即完全恢复原状，无迁移成本。
            optimization {
                enable = true
            }
            // 有 CI 密钥就用它；没有则回退 debug 签名。
            // 回退的目的是让 release 变体在没有密钥时仍能构建并安装（R8 冒烟必须真装真跑），
            // debug key 签出的包不可发布——正式发版由 CI 注入 RSSRADAR_KEYSTORE_PATH 走 release 密钥。
            signingConfig = if (System.getenv("RSSRADAR_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // rome 2.1.0 用 OSGi bnd 打包（Embed-Dependency）：
    //   rome.jar          内嵌 rome-utils-2.1.0.jar
    //   rome-modules.jar  内嵌 rome-2.1.0.jar + rome-utils-2.1.0.jar
    // 两个 rome-utils-2.1.0.jar 同名资源导致 AGP "2 files found with path"。
    // 内嵌 jar 仅 OSGi 运行时使用；Android 类路径下类由独立依赖提供
    // （rome POM 声明 com.rometools:rome-utils compile 依赖），内嵌副本冗余，全部排除。
    packaging {
        resources {
            excludes += setOf("rome-utils-2.1.0.jar", "rome-2.1.0.jar")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.compose.icons.lucide)
    ksp(libs.androidx.room.compiler)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
