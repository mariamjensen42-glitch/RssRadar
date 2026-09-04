// 纯 Kotlin/JVM 模块：不依赖任何 Android SDK / AndroidX。
// 承载数据模型：与 UI、持久化、网络均无关的领域数据结构。
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
    testImplementation(libs.junit)
}
