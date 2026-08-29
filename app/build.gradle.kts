plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
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
    implementation(libs.rome)
    implementation(libs.rome.modules)
    implementation(libs.jsoup)
    implementation(libs.readability4j)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
