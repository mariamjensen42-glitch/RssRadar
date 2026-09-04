/**
 * :core:model — 数据模型层（纯 Kotlin）。
 *
 * 承载与 Android、持久化、网络均无关的领域数据结构：
 * - 跨模块共享的值对象 / 实体（如 Feed、Article 的领域形态）
 * - 枚举、结果类型（如 Result 封装、内容来源标记）
 * - 纯数据转换（无副作用）
 *
 * 约定：
 * - 禁止 import 任何 androidx.* / android.* / Room / Retrofit / Compose 类型
 * - 可序列化需求出现后再引入 kotlinx-serialization 插件，不预置
 * - 上游：无；下游：:core:domain、:app
 */
package com.cycling.rssradar.core.model
