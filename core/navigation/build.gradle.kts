// 纯 Kotlin/JVM 模块：导航路由契约（类型安全目的地标识，ADR-0002）。
// 只声明「去哪」，不含任何 UI / Android 依赖 —— 各 feature 可放心依赖它。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    // @Serializable 目的地需要 serializer 同时出现在消费者的编译期。
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
