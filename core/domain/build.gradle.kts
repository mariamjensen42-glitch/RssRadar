// 纯 Kotlin/JVM 模块：不依赖任何 Android SDK / AndroidX。
// 承载领域逻辑：业务规则、纯计算、UseCase。
// 允许依赖 coroutines-core（JVM 库）以使用 suspend/Flow；禁止依赖 Room/Retrofit/Compose。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
